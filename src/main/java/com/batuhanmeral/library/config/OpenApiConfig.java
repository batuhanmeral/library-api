package com.batuhanmeral.library.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The parts of the OpenAPI description that springdoc cannot read from the controllers: the title,
 * and how a client authenticates.
 *
 * <p>Every operation needs a bearer token unless it says otherwise: public operations carry an
 * empty {@code @SecurityRequirements}, which replaces this default with "none". In Swagger UI,
 * "Authorize" takes the token from POST /auth/login once, and every request that needs it sends it
 * in the Authorization header.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** The name the operations refer to; any name works as long as both sides use it. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI libraryOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Library API")
                                .version("v1")
                                .description(
                                        "Books, authors, categories, members and their loans."
                                                + " Log in with POST /api/v1/auth/login and"
                                                + " paste the accessToken into Authorize.")
                                .license(new License().name("MIT")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_AUTH,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
