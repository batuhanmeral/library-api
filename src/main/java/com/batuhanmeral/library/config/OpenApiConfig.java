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
 * springdoc'un controller'lardan okuyamadığı OpenAPI parçaları: başlık ve kimlik doğrulama. Her
 * işlem varsayılan olarak bearer token ister; herkese açık olanlarda boş
 * {@code @SecurityRequirements} var.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** İşlemlerin başvurduğu ad. */
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
