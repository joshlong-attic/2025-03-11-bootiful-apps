package com.example.service.adoptions;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.json.ObjectToJsonTransformer;
import org.springframework.messaging.MessageChannel;
import org.springframework.modulith.events.Externalized;


@Externalized(MessagingConfiguration.ADOPTIONS_Q)
public record DogAdoptionEvent(int dogId) {
}


@Configuration
class MessagingConfiguration {


    static final String ADOPTIONS_Q = "adoptions";

    @Bean
    IntegrationFlow adoptionsFlow(
            @Value("file://${HOME}/Desktop/output") Resource resource,
            @Qualifier(ADOPTIONS_Q) MessageChannel input) throws Exception {
        var file = Files.outboundAdapter(resource.getFile()).autoCreateDirectory(true);

        return IntegrationFlow
                .from(input)
                .handle((GenericHandler<DogAdoptionEvent>) (payload, headers) -> {
                    System.out.println(payload.toString());
                    headers.forEach((k, v) -> System.out.println(k + '=' + v));
                    return payload;
                })
                .transform(new ObjectToJsonTransformer())
                .handle(file)
                .get();
    }

    @Bean(name = ADOPTIONS_Q)
    DirectChannelSpec messageChannel() {
        return MessageChannels.direct();
    }

}