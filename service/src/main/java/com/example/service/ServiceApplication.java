package com.example.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceApplication.class, args);
	}

/*

	@Bean
	ApplicationRunner youIncompleteMe(IncompleteEventPublications eventPublications) {
		return new ApplicationRunner() {
			@Override
			public void run(ApplicationArguments args) throws Exception {
				LockRegistry registry; //todo
				registry.executeLocked("incompleteEvents", new CheckedCallable<Object, Throwable>() {
					@Override
					public Object call() throws Throwable {
						eventPublications
								.resubmitIncompletePublications(ep -> ep.);
						return null;
					}
				}) ;

			}
		} ;
	}
*/

}
