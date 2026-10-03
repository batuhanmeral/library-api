package com.batuhanmeral.library.book;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class BookApiIntegrationTest {

    private static final String BOOKS = "/api/v1/books";

    @Autowired private MockMvcTester mvc;

    @Autowired private JdbcTemplate jdbcTemplate;

    private long authorId;
    private long fantasyId;
    private long classicsId;

    @BeforeEach
    void createAuthorAndCategories() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate, "loans", "book_categories", "books", "authors", "categories");
        authorId = idOf(post("/api/v1/authors", "{\"name\": \"J. R. R. Tolkien\"}"));
        fantasyId = idOf(post("/api/v1/categories", "{\"name\": \"Fantasy\"}"));
        classicsId = idOf(post("/api/v1/categories", "{\"name\": \"Classics\"}"));
    }

    @Test
    void createReturns201WithTheAuthorTheCategoriesAndAllCopiesAvailable() {
        MvcTestResult result =
                post(
                        BOOKS,
                        """
                        {"isbn": "9780547928227", "title": "The Hobbit", "publishedYear": 1937,
                         "pageCount": 310, "totalCopies": 3, "authorId": %d,
                         "categoryIds": [%d, %d]}
                        """
                                .formatted(authorId, fantasyId, classicsId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idOf(result);
        assertThat(result).headers().hasValue("Location", "http://localhost" + BOOKS + "/" + id);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("The Hobbit");
        assertThat(result).bodyJson().extractingPath("$.totalCopies").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.availableCopies").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.author.id").isEqualTo((int) authorId);
        assertThat(result).bodyJson().extractingPath("$.author.name").isEqualTo("J. R. R. Tolkien");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.categories[*].name")
                .asArray()
                .containsExactlyInAnyOrder("Fantasy", "Classics");
        assertThat(result).bodyJson().extractingPath("$.createdAt").isNotNull();
    }

    @Test
    void getByIdReturnsTheBookWithItsRelations() {
        long id = idOf(createHobbit());

        MvcTestResult result = mvc.get().uri(BOOKS + "/{id}", id).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.author.name").isEqualTo("J. R. R. Tolkien");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.categories[*].name")
                .asArray()
                .containsExactly("Fantasy");
    }

    @Test
    void getByIdReturns404WhenTheBookDoesNotExist() {
        assertProblem(
                mvc.get().uri(BOOKS + "/{id}", 9999).exchange(),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void listReturnsAllBooks() {
        createHobbit();
        post(BOOKS, bookJson("The Silmarillion", null, 1, authorId));

        assertThat(mvc.get().uri(BOOKS))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[*].title")
                .asArray()
                .containsExactlyInAnyOrder("The Hobbit", "The Silmarillion");
    }

    @Test
    void createReturns404WhenTheAuthorDoesNotExist() {
        MvcTestResult result = post(BOOKS, bookJson("The Hobbit", null, 1, 9999));

        assertProblem(result, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Author with id 9999 was not found.");
    }

    @Test
    void createReturns404WhenACategoryDoesNotExist() {
        MvcTestResult result =
                post(
                        BOOKS,
                        "{\"title\": \"The Hobbit\", \"totalCopies\": 1, \"authorId\": %d, \"categoryIds\": [%d, 9999]}"
                                .formatted(authorId, fantasyId));

        assertProblem(result, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Category with id 9999 was not found.");
    }

    @Test
    void createReturns409WhenTheIsbnIsAlreadyTaken() {
        createHobbit();

        MvcTestResult result = post(BOOKS, bookJson("Another", "9780547928227", 1, authorId));

        assertProblem(result, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A book with ISBN '9780547928227' already exists.");
    }

    @Test
    void createReturns400ForInvalidFields() {
        MvcTestResult result =
                post(
                        BOOKS,
                        "{\"isbn\": \"12-34\", \"title\": \"\", \"totalCopies\": -1, \"pageCount\": 0}");

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("isbn", "title", "totalCopies", "pageCount", "authorId");
    }

    @Test
    void updateReplacesTheFieldsTheAuthorAndTheCategories() {
        long id = idOf(createHobbit());
        long otherAuthor = idOf(post("/api/v1/authors", "{\"name\": \"Christopher Tolkien\"}"));

        MvcTestResult result =
                put(
                        id,
                        "{\"title\": \"The Hobbit (annotated)\", \"totalCopies\": 2, \"authorId\": %d, \"categoryIds\": [%d]}"
                                .formatted(otherAuthor, classicsId));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("The Hobbit (annotated)");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.author.name")
                .isEqualTo("Christopher Tolkien");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.categories[*].name")
                .asArray()
                .containsExactly("Classics");
        // PUT replaces the whole book: the ISBN that was not sent is now empty.
        assertThat(result).bodyJson().extractingPath("$.isbn").isNull();
    }

    @Test
    void updateOfTotalCopiesMovesAvailableCopiesByTheSameAmount() {
        long id = idOf(post(BOOKS, bookJson("The Hobbit", null, 3, authorId)));

        MvcTestResult result = put(id, bookJson("The Hobbit", null, 5, authorId));

        assertThat(result).bodyJson().extractingPath("$.totalCopies").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.availableCopies").isEqualTo(5);
    }

    @Test
    void updateReturns409WhenAnotherBookHasTheIsbn() {
        createHobbit();
        long id = idOf(post(BOOKS, bookJson("The Silmarillion", null, 1, authorId)));

        assertProblem(
                put(id, bookJson("The Silmarillion", "9780547928227", 1, authorId)),
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE");
    }

    @Test
    void deleteReturns204AndRemovesTheBook() {
        long id = idOf(createHobbit());

        assertThat(mvc.delete().uri(BOOKS + "/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri(BOOKS + "/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "book_categories")).isZero();
    }

    @Test
    void deletingACategoryUsedByABookReturns409() {
        createHobbit();

        MvcTestResult result = mvc.delete().uri("/api/v1/categories/{id}", fantasyId).exchange();

        assertProblem(result, HttpStatus.CONFLICT, "RESOURCE_IN_USE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(
                        "Category with id "
                                + fantasyId
                                + " is used by books and cannot be deleted.");
    }

    private MvcTestResult createHobbit() {
        return post(
                BOOKS,
                "{\"isbn\": \"9780547928227\", \"title\": \"The Hobbit\", \"totalCopies\": 1, \"authorId\": %d, \"categoryIds\": [%d]}"
                        .formatted(authorId, fantasyId));
    }

    private static String bookJson(String title, String isbn, int totalCopies, long authorId) {
        String isbnJson = isbn == null ? "null" : "\"" + isbn + "\"";
        return "{\"title\": \"%s\", \"isbn\": %s, \"totalCopies\": %d, \"authorId\": %d}"
                .formatted(title, isbnJson, totalCopies, authorId);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(long id, String json) {
        return mvc.put()
                .uri(BOOKS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }

    private static long idOf(MvcTestResult result) {
        String json =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }
}
