package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.book.openlibrary.OpenLibraryBook;
import com.batuhanmeral.library.book.openlibrary.OpenLibraryClient;
import com.jayway.jsonpath.JsonPath;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * POST /books with an ISBN fills in missing details from Open Library. The client is a mock: these
 * tests are about the rules of the endpoint, OpenLibraryClientTest is about the HTTP calls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BookIsbnLookupTest implements DatabaseTest {

    private static final String BOOKS = "/api/v1/books";
    private static final String ISBN = "9780441172719";
    private static final OpenLibraryBook DUNE =
            new OpenLibraryBook("Dune", "A desert planet.", 1987, 535);

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private OpenLibraryClient openLibrary;

    private long authorId;

    @BeforeEach
    void createAnAuthor() {
        authorId = idOf(post("/api/v1/authors", "{\"name\": \"Frank Herbert\"}"));
    }

    @Test
    void anIsbnAloneIsEnoughWhenOpenLibraryKnowsTheBook() {
        when(openLibrary.findByIsbn(ISBN)).thenReturn(Optional.of(DUNE));

        MvcTestResult result = create("{\"isbn\": \"%s\"}".formatted(ISBN));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Dune");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("A desert planet.");
        assertThat(result).bodyJson().extractingPath("$.publishedYear").isEqualTo(1987);
        assertThat(result).bodyJson().extractingPath("$.pageCount").isEqualTo(535);
        assertThat(result).bodyJson().extractingPath("$.isbn").isEqualTo(ISBN);
    }

    @Test
    void valuesInTheRequestWinOverOpenLibrary() {
        when(openLibrary.findByIsbn(ISBN)).thenReturn(Optional.of(DUNE));

        MvcTestResult result =
                create(
                        "{\"isbn\": \"%s\", \"title\": \"Dune (40th anniversary)\", \"publishedYear\": 2005}"
                                .formatted(ISBN));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Dune (40th anniversary)");
        assertThat(result).bodyJson().extractingPath("$.publishedYear").isEqualTo(2005);
        assertThat(result).bodyJson().extractingPath("$.pageCount").isEqualTo(535);
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("A desert planet.");
    }

    @Test
    void openLibraryIsNotAskedWhenNothingIsMissingOrThereIsNoIsbn() {
        create(
                "{\"isbn\": \"%s\", \"title\": \"Dune\", \"description\": \"Sand.\", \"publishedYear\": 1965, \"pageCount\": 412}"
                        .formatted(ISBN));
        create("{\"title\": \"Dune Messiah\"}");

        verify(openLibrary, never()).findByIsbn(anyString());
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "books")).isEqualTo(2);
    }

    @Test
    void withoutAnIsbnTheTitleIsRequired() {
        MvcTestResult result = create("{}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("must not be blank when no isbn is given");
    }

    @Test
    void anUnknownIsbnWithoutATitleIs422() {
        when(openLibrary.findByIsbn(ISBN)).thenReturn(Optional.empty());

        MvcTestResult result = create("{\"isbn\": \"%s\"}".formatted(ISBN));

        assertThat(result)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("ISBN_NOT_FOUND");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Open Library has no book with ISBN " + ISBN + ". Send the title.");
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "books")).isZero();
    }

    @Test
    void aBookOpenLibraryKnowsWithoutATitleIs422Too() {
        when(openLibrary.findByIsbn(ISBN))
                .thenReturn(Optional.of(new OpenLibraryBook(null, null, 1987, 535)));

        assertThat(create("{\"isbn\": \"%s\"}".formatted(ISBN)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("ISBN_NOT_FOUND");
    }

    @Test
    void anUnreachableOpenLibraryWithoutATitleIs503() {
        when(openLibrary.findByIsbn(ISBN))
                .thenThrow(new ResourceAccessException("timed out", new SocketTimeoutException()));

        MvcTestResult timeout = create("{\"isbn\": \"%s\"}".formatted(ISBN));

        assertThat(timeout)
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(timeout)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
        assertThat(timeout)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(
                        "Open Library could not be reached. Send the title, or try again later.");
        assertThat(timeout).bodyText().doesNotContain("timed out");

        doThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null))
                .when(openLibrary)
                .findByIsbn(ISBN);
        assertThat(create("{\"isbn\": \"%s\"}".formatted(ISBN)))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "books")).isZero();
    }

    @Test
    void withATitleAnOpenLibraryErrorOnlyLeavesAWarning(CapturedOutput output) {
        when(openLibrary.findByIsbn(ISBN))
                .thenThrow(new ResourceAccessException("timed out", new SocketTimeoutException()));

        MvcTestResult result = create("{\"isbn\": \"%s\", \"title\": \"Dune\"}".formatted(ISBN));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.pageCount").isNull();
        assertThat(output)
                .contains(
                        "Open Library lookup for ISBN "
                                + ISBN
                                + " failed (ResourceAccessException); saving the book as sent");
    }

    @Test
    void withATitleAnUnknownIsbnOnlyLeavesAWarning(CapturedOutput output) {
        when(openLibrary.findByIsbn(ISBN)).thenReturn(Optional.empty());

        assertThat(create("{\"isbn\": \"%s\", \"title\": \"Dune\"}".formatted(ISBN)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(output).contains("Open Library has no book with ISBN " + ISBN);
    }

    @Test
    void openLibraryIsCalledOutsideAnyDatabaseTransaction() {
        AtomicBoolean transactionDuringCall = new AtomicBoolean();
        when(openLibrary.findByIsbn(ISBN))
                .thenAnswer(invocation -> {
                    transactionDuringCall.set(
                            TransactionSynchronizationManager.isActualTransactionActive());
                    return Optional.of(DUNE);
                });

        assertThat(create("{\"isbn\": \"%s\"}".formatted(ISBN))).hasStatus(HttpStatus.CREATED);
        assertThat(transactionDuringCall)
                .as("a transaction was open during the HTTP call")
                .isFalse();
    }

    @Test
    void putStillNeedsATitleAndDoesNotAskOpenLibrary() {
        when(openLibrary.findByIsbn(ISBN)).thenReturn(Optional.of(DUNE));
        long id = idOf(create("{\"isbn\": \"%s\"}".formatted(ISBN)));

        MvcTestResult result =
                mvc.put()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(BOOKS + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"isbn\": \"%s\", \"totalCopies\": 1, \"authorId\": %d}"
                                        .formatted(ISBN, authorId))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
        verify(openLibrary).findByIsbn(ISBN);
    }

    @Test
    void aTakenIsbnOrAnUnknownAuthorIsRejectedBeforeAskingOpenLibrary() {
        when(openLibrary.findByIsbn(ISBN)).thenReturn(Optional.of(DUNE));
        create("{\"isbn\": \"%s\"}".formatted(ISBN));
        clearInvocations(openLibrary);

        assertThat(create("{\"isbn\": \"%s\"}".formatted(ISBN))).hasStatus(HttpStatus.CONFLICT);
        assertThat(
                        post(
                                BOOKS,
                                "{\"isbn\": \"9780140328721\", \"totalCopies\": 1, \"authorId\": 999999}"))
                .hasStatus(HttpStatus.NOT_FOUND);

        verify(openLibrary, never()).findByIsbn(anyString());
    }

    @Test
    void aLookupRejectedForTooManyConcurrentCallsCountsAsUnavailable() {
        doThrow(new InvocationRejectedException("Concurrency limit reached", openLibrary))
                .when(openLibrary)
                .findByIsbn(ISBN);

        assertThat(create("{\"isbn\": \"%s\"}".formatted(ISBN)))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(create("{\"isbn\": \"%s\", \"title\": \"Dune\"}".formatted(ISBN)))
                .hasStatus(HttpStatus.CREATED);
    }

    /** Adds the required totalCopies and authorId to the given fields. */
    private MvcTestResult create(String json) {
        String body =
                json.replaceFirst(
                        "\\{",
                        "{\"totalCopies\": 1, \"authorId\": %d%s"
                                .formatted(authorId, json.equals("{}") ? "" : ", "));
        return post(BOOKS, body);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post()
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static long idOf(MvcTestResult result) {
        Number id =
                JsonPath.read(
                        new String(
                                result.getResponse().getContentAsByteArray(),
                                StandardCharsets.UTF_8),
                        "$.id");
        return id.longValue();
    }
}
