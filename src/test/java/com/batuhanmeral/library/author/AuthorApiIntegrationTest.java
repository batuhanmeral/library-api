package com.batuhanmeral.library.author;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorApiIntegrationTest {

    private static final String AUTHORS = "/api/v1/authors";

    @Autowired private MockMvcTester mvc;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate, "loans", "book_categories", "books", "authors", "categories");
    }

    @Test
    void createReturns201WithLocationAndAuditTimestamps() {
        MvcTestResult result =
                post(
                        AUTHORS,
                        """
                        {"name": "Ursula K. Le Guin", "biography": "American author",
                         "birthDate": "1929-10-21"}
                        """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idOf(result);
        assertThat(result).headers().hasValue("Location", "http://localhost" + AUTHORS + "/" + id);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Ursula K. Le Guin");
        assertThat(result).bodyJson().extractingPath("$.birthDate").isEqualTo("1929-10-21");
        assertThat(result).bodyJson().extractingPath("$.createdAt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.updatedAt").isNotNull();
    }

    @Test
    void getByIdReturnsTheAuthor() {
        long id = idOf(post(AUTHORS, "{\"name\": \"Tolkien\"}"));

        assertThat(mvc.get().uri(AUTHORS + "/{id}", id))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Tolkien");
    }

    @Test
    void getByIdReturns404WhenTheAuthorDoesNotExist() {
        MvcTestResult result = mvc.get().uri(AUTHORS + "/{id}", 9999).exchange();

        assertProblem(result, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Author with id 9999 was not found.");
    }

    @Test
    void listReturnsAPageOfAuthorsSortedByName() {
        post(AUTHORS, "{\"name\": \"Tolkien\"}");
        post(AUTHORS, "{\"name\": \"Le Guin\"}");

        MvcTestResult result = mvc.get().uri(AUTHORS + "?size=1").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].name")
                .asArray()
                .containsExactly("Le Guin");
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
    }

    @Test
    void listReturns400ForAFieldThatCannotBeSortedBy() {
        assertProblem(
                mvc.get().uri(AUTHORS + "?sort=biography").exchange(),
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER");
    }

    @Test
    void updateChangesTheFieldsAndTheModifiedTimestamp() {
        MvcTestResult created = post(AUTHORS, "{\"name\": \"Tolkein\"}");
        long id = idOf(created);
        Instant createdAt = Instant.parse(stringAt(created, "$.createdAt"));

        MvcTestResult result =
                put(id, "{\"name\": \"J. R. R. Tolkien\", \"birthDate\": \"1892-01-03\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("J. R. R. Tolkien");
        assertThat(Instant.parse(stringAt(result, "$.createdAt"))).isEqualTo(createdAt);
        assertThat(Instant.parse(stringAt(result, "$.updatedAt"))).isAfter(createdAt);
    }

    @Test
    void updateReturns404WhenTheAuthorDoesNotExist() {
        assertProblem(
                put(9999, "{\"name\": \"Tolkien\"}"), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    @Test
    void createReturns400WhenTheNameIsBlankOrTheBirthDateIsInTheFuture() {
        MvcTestResult result = post(AUTHORS, "{\"name\": \" \", \"birthDate\": \"2999-01-01\"}");

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("name", "birthDate");
    }

    @Test
    void deleteReturns204AndRemovesTheAuthor() {
        long id = idOf(post(AUTHORS, "{\"name\": \"Tolkien\"}"));

        assertThat(mvc.delete().uri(AUTHORS + "/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri(AUTHORS + "/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns409WhenTheAuthorHasBooks() {
        long id = idOf(post(AUTHORS, "{\"name\": \"Tolkien\"}"));
        post(
                "/api/v1/books",
                "{\"title\": \"The Hobbit\", \"totalCopies\": 1, \"authorId\": " + id + "}");

        MvcTestResult result = mvc.delete().uri(AUTHORS + "/{id}", id).exchange();

        assertProblem(result, HttpStatus.CONFLICT, "RESOURCE_IN_USE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Author with id " + id + " has books and cannot be deleted.");
    }

    @Test
    void deleteReturns404WhenTheAuthorDoesNotExist() {
        assertProblem(
                mvc.delete().uri(AUTHORS + "/{id}", 9999).exchange(),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void booksOfAnAuthorListsOnlyThatAuthorsBooks() {
        long tolkien = idOf(post(AUTHORS, "{\"name\": \"Tolkien\"}"));
        long leGuin = idOf(post(AUTHORS, "{\"name\": \"Le Guin\"}"));
        post(
                "/api/v1/books",
                "{\"title\": \"The Hobbit\", \"totalCopies\": 1, \"authorId\": " + tolkien + "}");
        post(
                "/api/v1/books",
                "{\"title\": \"Earthsea\", \"totalCopies\": 1, \"authorId\": " + leGuin + "}");

        MvcTestResult result = mvc.get().uri(AUTHORS + "/{id}/books", tolkien).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].title")
                .asArray()
                .containsExactly("The Hobbit");
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void booksOfAnAuthorReturns404WhenTheAuthorDoesNotExist() {
        assertProblem(
                mvc.get().uri(AUTHORS + "/{id}/books", 9999).exchange(),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(long id, String json) {
        return mvc.put()
                .uri(AUTHORS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }

    private static long idOf(MvcTestResult result) {
        Number id = JsonPath.read(body(result), "$.id");
        return id.longValue();
    }

    private static String stringAt(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
