package com.campuslostfound.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI campusLostAndFoundApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Campus Lost & Found API")
                        .version("1.0.0")
                        .description("""
                                Centralized lost-and-found platform for a university campus. Report lost
                                and found items, search and filter listings, receive scored potential
                                matches with explanations, verify ownership through a claim workflow,
                                contact the other party in-app, and flag suspicious listings.

                                Send the token from POST /api/v1/auth/login as `Authorization: Bearer <token>`.
                                """)
                        .license(new License().name("Portfolio project - no warranty")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
