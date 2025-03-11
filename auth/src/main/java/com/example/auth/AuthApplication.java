package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;

import static org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer.authorizationServer;

@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    SecurityFilterChain mySecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(ae -> ae.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .webAuthn(wa -> wa
                        .rpName("Bootiful Passkeys")
                        .rpId("localhost")
                        .allowedOrigins("http://localhost:8080", "http://localhost:8081",
                                "http://localhost:9090",
                                "http://127.0.0.1:8081", "http://127.0.0.1:8080"))
                .with(authorizationServer(), as -> as.oidc(Customizer.withDefaults()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        var users = Set.of(User.withUsername("jlong")
                .password(passwordEncoder.encode("pw"))
                .roles("USER")
                .build()
        );
        return new InMemoryUserDetailsManager(users);
    }

}
