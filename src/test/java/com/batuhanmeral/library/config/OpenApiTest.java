package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The OpenAPI description springdoc builds from the controllers, and the Swagger UI page that shows
 * it. Both are public: the description lists paths and fields, nothing a client of the API cannot
 * see anyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Test
    void theDescriptionListsTheApiAndNotTheActuator() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.info.title").isEqualTo("Library API");
        assertThat(result).bodyJson().hasPath("$.paths['/api/v1/books/{id}'].get");
        assertThat(result).bodyJson().hasPath("$.paths['/api/v1/loans'].post");
        assertThat(result).bodyJson().doesNotHavePath("$.paths['/actuator/health']");
    }

    @Test
    void requestsCarryABearerTokenUnlessTheEndpointIsPublic() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result)
                .bodyJson()
                .extractingPath("$.components.securitySchemes.bearerAuth.scheme")
                .isEqualTo("bearer");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.security[0].bearerAuth")
                .asArray()
                .isEmpty();
        // Public endpoints override the global requirement with an empty list.
        assertThat(result)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/auth/login'].post.security")
                .asArray()
                .isEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/books'].get.security")
                .asArray()
                .isEmpty();
        assertThat(result).bodyJson().doesNotHavePath("$.paths['/api/v1/books'].post.security");
    }

    /** Search filters and paging are query parameters, not one object parameter. */
    @Test
    void searchFiltersAndPagingAreSeparateQueryParameters() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/books'].get.parameters[*].name")
                .asArray()
                .contains("title", "authorId", "available", "page", "size", "sort");
    }

    @Test
    void swaggerUiIsPublic() {
        MvcTestResult result = mvc.get().uri("/swagger-ui.html").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getRedirectedUrl()).contains("/swagger-ui/index.html");
        assertThat(mvc.get().uri("/swagger-ui/index.html").exchange()).hasStatus(HttpStatus.OK);
    }
}
