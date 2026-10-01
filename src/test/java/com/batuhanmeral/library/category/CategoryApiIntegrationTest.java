package com.batuhanmeral.library.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryApiIntegrationTest {

    private static final String CATEGORIES = "/api/v1/categories";

    @Autowired private MockMvcTester mvc;

    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void cleanDatabase() {
        categoryRepository.deleteAll();
    }

    @Test
    void createReturns201WithLocationHeaderAndTheNewCategory() {
        MvcTestResult result =
                post("{\"name\": \"Science\", \"description\": \"Popular science\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idOf(result);
        assertThat(result)
                .headers()
                .hasValue("Location", "http://localhost" + CATEGORIES + "/" + id);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Science");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Popular science");
    }

    @Test
    void createIgnoresAnIdSentInTheRequestBody() {
        long existingId = idOf(post("{\"name\": \"History\"}"));

        MvcTestResult result = post("{\"id\": " + existingId + ", \"name\": \"Poetry\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(idOf(result)).isNotEqualTo(existingId);
        assertThat(categoryRepository.findById(existingId))
                .get()
                .extracting(Category::getName)
                .isEqualTo("History");
    }

    @Test
    void getByIdReturnsTheCategory() {
        long id = idOf(post("{\"name\": \"Science\"}"));

        assertThat(mvc.get().uri(CATEGORIES + "/{id}", id))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Science");
    }

    @Test
    void getByIdReturns404WhenTheCategoryDoesNotExist() {
        assertThat(mvc.get().uri(CATEGORIES + "/{id}", 9999)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void listReturnsAllCategories() {
        post("{\"name\": \"Science\"}");
        post("{\"name\": \"History\"}");

        assertThat(mvc.get().uri(CATEGORIES))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactlyInAnyOrder("Science", "History");
    }

    @Test
    void updateChangesNameAndDescription() {
        long id = idOf(post("{\"name\": \"Sciense\"}"));

        MvcTestResult result =
                mvc.put()
                        .uri(CATEGORIES + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Science\", \"description\": \"Fixed typo\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Science");
        assertThat(categoryRepository.findById(id))
                .get()
                .extracting(Category::getDescription)
                .isEqualTo("Fixed typo");
    }

    @Test
    void updateReturns404WhenTheCategoryDoesNotExist() {
        assertThat(
                        mvc.put()
                                .uri(CATEGORIES + "/{id}", 9999)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"Science\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns204AndRemovesTheCategory() {
        long id = idOf(post("{\"name\": \"Science\"}"));

        assertThat(mvc.delete().uri(CATEGORIES + "/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(categoryRepository.existsById(id)).isFalse();
    }

    @Test
    void deleteReturns404WhenTheCategoryDoesNotExist() {
        assertThat(mvc.delete().uri(CATEGORIES + "/{id}", 9999)).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult post(String json) {
        return mvc.post()
                .uri(CATEGORIES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static long idOf(MvcTestResult result) {
        String json =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }
}
