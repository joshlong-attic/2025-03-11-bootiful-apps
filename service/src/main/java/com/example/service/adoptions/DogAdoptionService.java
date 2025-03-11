package com.example.service.adoptions;

import com.example.service.grpc.AdoptionsGrpc;
import com.example.service.grpc.DogsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
class DogAdoptionGrpcService extends AdoptionsGrpc.AdoptionsImplBase {

    private final DogAdoptionService service;

    DogAdoptionGrpcService(DogAdoptionService service) {
        this.service = service;
    }

    @Override
    public void all(Empty request, StreamObserver<DogsResponse> responseObserver) {

        var all = service.all()
                .stream()
                .map(ourDog -> com.example.service.grpc.Dog.newBuilder()
                        .setName(ourDog.name())
                        .setId(ourDog.id())
                        .setDescription(ourDog.description())
                        .build())
                .toList();

        responseObserver.onNext(DogsResponse.newBuilder()
                .addAllDogs(all)
                .build());
        responseObserver.onCompleted();

    }
}


@Controller
class DogAdoptionGraphQlController {

    private final DogAdoptionService dogAdoptionService;

    DogAdoptionGraphQlController(DogAdoptionService dogAdoptionService) {
        this.dogAdoptionService = dogAdoptionService;
    }

    @QueryMapping
    Collection<Dog> dogs() {
        return dogAdoptionService.all();
    }
}


@Controller
@ResponseBody
class DogAdoptionHttpController {

    private final DogAdoptionService service;

    DogAdoptionHttpController(DogAdoptionService service) {
        this.service = service;
    }

    @GetMapping("/assistant")
    String assistant(Principal principal, @RequestParam String question) {
        return service.assistant(principal.getName(), question);
    }


    @GetMapping("/dogs")
    Collection<Dog> dogs() {
        return this.service.all();
    }

    @PostMapping("/dogs/{dogId}/adoptions")
    void adopt(@PathVariable int dogId, @RequestParam String owner) {
        this.service.adopt(dogId, owner);
    }
}

@Controller
@ResponseBody
class MeController {

    @GetMapping("/me")
    Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }
}

@Component
class DogAdoptionScheduler {

    private final ObjectMapper objectMapper;

    DogAdoptionScheduler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(description = "schedule an appointment to adopt a dog")
    String scheduleDogForPickup(@ToolParam int dogId, @ToolParam String name) throws Exception {
        System.out.println("scheduling for pickup " + dogId + " " + name);
        return this.objectMapper.writeValueAsString(
                Instant.now().plus(3, ChronoUnit.DAYS));
    }
}

@Service
@Transactional
class DogAdoptionService {

    private final DogRepository dogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatClient chatClient;
    private final Map<String, PromptChatMemoryAdvisor> promptChatMemoryAdvisorMap
            = new ConcurrentHashMap<>();


    DogAdoptionService(DogRepository dogRepository, ApplicationEventPublisher eventPublisher, ChatClient chatClient) {
        this.dogRepository = dogRepository;
        this.eventPublisher = eventPublisher;
        this.chatClient = chatClient;
    }

    void adopt(int dogId, String owner) {
        this.dogRepository.findById(dogId).ifPresent(dog -> {
            var updated = dogRepository.save(new Dog(
                    dogId, dog.name(), owner, dog.description()
            ));
            System.out.println("adopted [" + updated + "]");
            this.eventPublisher.publishEvent(new DogAdoptionEvent(dogId));
        });

    }

    String assistant(String user, String question) {

        var advisor = this.promptChatMemoryAdvisorMap
                .computeIfAbsent(user, _ ->
                        PromptChatMemoryAdvisor.builder(new InMemoryChatMemory()).build());

        return this.chatClient
                .prompt()
                .user(question)
                .advisors(advisor)
                .call()
                .content(); // todo
    }

    Collection<Dog> all() {
        return dogRepository.findAll();
    }
}

@Configuration
class AiConfiguration {

    @Bean
    VectorStore inMemoryVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    ChatClient chatClient(DogAdoptionScheduler scheduler,
                          ChatClient.Builder builder,
                          DogRepository repository,
                          VectorStore vectorStore) {

        if (false)
            repository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name: %s, description: %s"
                        .formatted(dog.id(), dog.name(), dog.description()));
                vectorStore.add(List.of(dogument));
            });

        var system = """
                You are an AI powered assistant to help people adopt a dog from the adoption\s
                agency named Pooch Palace with locations in Amsterdam, Seoul, Tokyo, Singapore, Paris,\s
                Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                will be presented below. If there is no information, then return a polite response suggesting we\s
                don't have any dogs available.
                """;
        return builder
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .defaultTools(scheduler)
                .defaultSystem(system)
                .build();
    }
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

// look mom, no Lombok!
record Dog(@Id int id, String name, String owner, String description) {
}