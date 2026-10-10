package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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

    @Autowired private JwtService jwt;

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
    void listReturnsTheFirstPageSortedByTitle() {
        post(BOOKS, bookJson("The Silmarillion", null, 1, authorId));
        createHobbit();

        MvcTestResult result = mvc.get().uri(BOOKS).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].title")
                .asArray()
                .containsExactly("The Hobbit", "The Silmarillion");
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(1);
    }

    @Test
    void listReturnsTheRequestedPageAndSortOrder() {
        for (String title : List.of("A", "B", "C")) {
            post(BOOKS, bookJson(title, null, 1, authorId));
        }

        MvcTestResult result = mvc.get().uri(BOOKS + "?page=1&size=2&sort=title,desc").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].title")
                .asArray()
                .containsExactly("A");
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
    }

    @Test
    void listCapsThePageSizeAt100() {
        assertThat(mvc.get().uri(BOOKS + "?size=500"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.size")
                .isEqualTo(100);
    }

    @Test
    void listReturns400ForAFieldThatCannotBeSortedBy() {
        // An unknown property used to fail inside the query (PropertyReferenceException, 500); a
        // path into a relation would sort on data the API does not show.
        for (String sort : List.of("price", "author.biography", "categories.name")) {
            MvcTestResult result = mvc.get().uri(BOOKS + "?sort={sort}", sort).exchange();

            assertProblem(result, HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo(
                            "Cannot sort by this field. Sortable fields: availableCopies,"
                                    + " createdAt, id, publishedYear, title.");
        }
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
        // No "title": since step 8 a new book given with an ISBN may leave the title out (it comes
        // from Open Library). PUT still requires it; BookIsbnLookupTest covers both.
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("isbn", "totalCopies", "pageCount", "authorId");
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
    void updateReturns409WhenTheTotalWouldDropBelowTheCopiesOnLoan() {
        long id = idOf(post(BOOKS, bookJson("The Hobbit", null, 3, authorId)));
        // Two copies out on loan; the loan endpoints come later in this step.
        jdbcTemplate.update("update books set available_copies = 1 where id = ?", id);

        MvcTestResult result = put(id, bookJson("The Hobbit", null, 1, authorId));

        assertProblem(result, HttpStatus.CONFLICT, "COPIES_ON_LOAN");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("2 copies of this book are on loan; totalCopies cannot be lower.");
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
    void searchByTitleFindsPartsOfTheTitleIgnoringCase() {
        createHobbit();
        post(BOOKS, bookJson("The Silmarillion", null, 1, authorId));

        assertThat(titles(BOOKS + "?title={title}", "HOBB")).containsExactly("The Hobbit");
        assertThat(titles(BOOKS + "?title={title}", "the")).hasSize(2);
    }

    @Test
    void searchByTitleTreatsPercentAndUnderscoreAsPlainCharacters() {
        for (String title : List.of("50% Off", "500 Days", "A_B", "AxB")) {
            post(BOOKS, bookJson(title, null, 1, authorId));
        }

        // Unescaped, "50%" would match "500 Days" and "A_B" would match "AxB": in LIKE, % is
        // any text and _ is any single character.
        assertThat(titles(BOOKS + "?title={title}", "50%")).containsExactly("50% Off");
        assertThat(titles(BOOKS + "?title={title}", "a_b")).containsExactly("A_B");
    }

    @Test
    void searchCombinesAuthorCategoryAvailabilityAndYearFilters() {
        long otherAuthor = idOf(post("/api/v1/authors", "{\"name\": \"Ursula K. Le Guin\"}"));
        post(BOOKS, searchBookJson("The Hobbit", 1937, 1, authorId, fantasyId));
        post(BOOKS, searchBookJson("The Silmarillion", 1977, 1, authorId, fantasyId));
        post(BOOKS, searchBookJson("Unfinished Tales", 1980, 0, authorId, fantasyId));
        post(BOOKS, searchBookJson("Letters", 1981, 1, authorId, classicsId));
        post(BOOKS, searchBookJson("A Wizard of Earthsea", 1968, 1, otherAuthor, fantasyId));

        assertThat(titles(BOOKS + "?authorId={id}", authorId)).hasSize(4);
        assertThat(titles(BOOKS + "?categoryId={id}", classicsId)).containsExactly("Letters");
        assertThat(titles(BOOKS + "?available=false")).containsExactly("Unfinished Tales");
        assertThat(titles(BOOKS + "?yearFrom=1960&yearTo=1979"))
                .containsExactly("A Wizard of Earthsea", "The Silmarillion");
        assertThat(
                        titles(
                                BOOKS + "?authorId={a}&categoryId={c}&available=true&yearFrom=1930",
                                authorId,
                                fantasyId))
                .containsExactly("The Hobbit", "The Silmarillion");
    }

    @Test
    void filteringByACategoryStillReturnsAllCategoriesOfTheBook() {
        createBook(
                "{\"title\": \"The Hobbit\", \"totalCopies\": 1, \"authorId\": %d, \"categoryIds\": [%d, %d]}"
                        .formatted(authorId, fantasyId, classicsId));

        assertThat(mvc.get().uri(BOOKS + "?categoryId={id}", fantasyId))
                .bodyJson()
                .extractingPath("$.content[0].categories[*].name")
                .asArray()
                .containsExactlyInAnyOrder("Fantasy", "Classics");
    }

    @Test
    void searchReturns400WhenTheYearRangeEndsBeforeItStarts() {
        MvcTestResult result = mvc.get().uri(BOOKS + "?yearFrom=2000&yearTo=1990").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("yearFrom must not be greater than yearTo.");
    }

    @Test
    void searchReturns400ForFilterValuesOutOfRange() {
        MvcTestResult result = mvc.get().uri(BOOKS + "?yearTo=0&authorId=-1").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("yearTo", "authorId");
    }

    @Test
    void searchReturns400ForAFilterValueOfTheWrongType() {
        // The record is built through its constructor, so a value that cannot be converted
        // stops the binding: only that field is reported, the others are not validated.
        MvcTestResult result = mvc.get().uri(BOOKS + "?yearFrom=abc&yearTo=0").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("yearFrom");
    }

    @Test
    void patchChangesOnlyTheFieldsThatAreSent() {
        long id = idOf(createHobbit());

        MvcTestResult result = patch(id, "{\"pageCount\": 320, \"publishedYear\": 1937}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.pageCount").isEqualTo(320);
        assertThat(result).bodyJson().extractingPath("$.publishedYear").isEqualTo(1937);
        // Not sent, so not changed. PUT would have cleared the ISBN and the categories.
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("The Hobbit");
        assertThat(result).bodyJson().extractingPath("$.isbn").isEqualTo("9780547928227");
        assertThat(result).bodyJson().extractingPath("$.totalCopies").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.categories[*].name")
                .asArray()
                .containsExactly("Fantasy");
    }

    @Test
    void patchTreatsNullAsNotSent() {
        long id = idOf(createHobbit());

        MvcTestResult result = patch(id, "{\"isbn\": null, \"title\": null}");

        // A field cannot be cleared with PATCH: null and a missing field mean the same here.
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.isbn").isEqualTo("9780547928227");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("The Hobbit");
    }

    @Test
    void patchChangesTheAuthorTheCategoriesAndTheCopiesWhenSent() {
        long id = idOf(createHobbit());
        long otherAuthor = idOf(post("/api/v1/authors", "{\"name\": \"Christopher Tolkien\"}"));

        MvcTestResult result =
                patch(
                        id,
                        "{\"authorId\": %d, \"categoryIds\": [], \"totalCopies\": 4}"
                                .formatted(otherAuthor));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.author.name")
                .isEqualTo("Christopher Tolkien");
        // An empty list is a value: it removes every category.
        assertThat(result).bodyJson().extractingPath("$.categories").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.totalCopies").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.availableCopies").isEqualTo(4);
    }

    @Test
    void patchValidatesTheFieldsThatAreSent() {
        long id = idOf(createHobbit());

        MvcTestResult result =
                patch(id, "{\"title\": \" \", \"totalCopies\": -1, \"isbn\": \"12\"}");

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("title", "totalCopies", "isbn");
    }

    @Test
    void patchReturns404ForAnUnknownBookAnd409ForATakenIsbn() {
        createHobbit();
        long id = idOf(post(BOOKS, bookJson("The Silmarillion", null, 1, authorId)));

        assertProblem(
                patch(9999, "{\"pageCount\": 1}"), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertProblem(
                patch(id, "{\"isbn\": \"9780547928227\"}"),
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE");
    }

    @Test
    void createReturns400ForMoreThan20Categories() {
        String ids =
                LongStream.rangeClosed(1, 21)
                        .mapToObj(Long::toString)
                        .collect(Collectors.joining(","));

        MvcTestResult result =
                post(
                        BOOKS,
                        "{\"title\": \"T\", \"totalCopies\": 1, \"authorId\": %d, \"categoryIds\": [%s]}"
                                .formatted(authorId, ids));

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("categoryIds");
    }

    @Test
    void deleteReturns204AndRemovesTheBook() {
        long id = idOf(createHobbit());

        assertThat(
                        mvc.delete()
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                                .uri(BOOKS + "/{id}", id))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri(BOOKS + "/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "book_categories")).isZero();
    }

    @Test
    void deletingACategoryUsedByABookReturns409() {
        createHobbit();

        MvcTestResult result =
                mvc.delete()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri("/api/v1/categories/{id}", fantasyId)
                        .exchange();

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

    private void createBook(String json) {
        assertThat(post(BOOKS, json)).hasStatus(HttpStatus.CREATED);
    }

    /** The titles on the first page of a search, in the order the API returned them. */
    private List<String> titles(String uriTemplate, Object... uriVariables) {
        MvcTestResult result = mvc.get().uri(uriTemplate, uriVariables).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        String json =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.content[*].title");
    }

    private static String searchBookJson(
            String title, int year, int totalCopies, long authorId, long categoryId) {
        return """
        {"title": "%s", "publishedYear": %d, "totalCopies": %d, "authorId": %d,
         "categoryIds": [%d]}
        """
                .formatted(title, year, totalCopies, authorId, categoryId);
    }

    private static String bookJson(String title, String isbn, int totalCopies, long authorId) {
        String isbnJson = isbn == null ? "null" : "\"" + isbn + "\"";
        return "{\"title\": \"%s\", \"isbn\": %s, \"totalCopies\": %d, \"authorId\": %d}"
                .formatted(title, isbnJson, totalCopies, authorId);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post()
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult patch(long id, String json) {
        return mvc.patch()
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                .uri(BOOKS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult put(long id, String json) {
        return mvc.put()
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
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
