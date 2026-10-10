package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.notification.EmailNotificationService;
import com.batuhanmeral.library.notification.Recipient;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Confirmation emails for borrowing and returning. They go out only after the loan is committed;
 * LoanConcurrencyTest checks the same for borrows that lose a race and are retried.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoanNotificationTest implements DatabaseTest {

    private static final Recipient ADA = new Recipient("ada@example.com", null);

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private UserRepository userRepository;

    @Autowired private Clock clock;

    @MockitoSpyBean private EmailNotificationService email;

    private long authorId;
    private long memberId;

    @BeforeEach
    void createAnAuthorAndAMember() {
        authorId = idOf(post("/api/v1/authors", "{\"name\": \"Frank Herbert\"}"));
        memberId =
                userRepository
                        .save(new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER))
                        .getId();
    }

    @Test
    void aBorrowSendsOneConfirmationWithTheTitleAndDueDate() {
        long bookId = createBook("Dune", 1);

        assertThat(borrow(bookId)).hasStatus(HttpStatus.CREATED);

        String dueDate = LocalDate.now(clock).plusDays(14).toString();
        verify(email).send(eq(ADA), contains("You borrowed \"Dune\""));
        verify(email).send(eq(ADA), contains(dueDate));
        verify(email, times(1)).send(any(), anyString());
    }

    @Test
    void aReturnSendsAConfirmationToo() {
        long loanId = idOf(borrow(createBook("Dune", 1)));

        mvc.post()
                .uri("/api/v1/loans/{id}/return", loanId)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                .exchange();

        verify(email).send(eq(ADA), contains("You returned \"Dune\""));
        verify(email, times(2)).send(any(), anyString());
    }

    @Test
    void aRejectedBorrowSendsNothing() {
        long bookId = createBook("Dune", 1);
        borrow(bookId);
        verify(email, times(1)).send(any(), anyString());

        assertThat(borrow(bookId)).hasStatus(HttpStatus.CONFLICT);

        verify(email, times(1)).send(any(), anyString());
    }

    @Test
    void aFailingEmailDoesNotUndoTheLoan() {
        doThrow(new IllegalStateException("mail server down")).when(email).send(any(), anyString());
        long bookId = createBook("Dune", 1);

        MvcTestResult result = borrow(bookId);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "loans")).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select available_copies from books where id = ?",
                                Integer.class,
                                bookId))
                .isZero();
    }

    private MvcTestResult borrow(long bookId) {
        return mvc.post()
                .uri("/api/v1/loans")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\": %d}".formatted(bookId))
                .exchange();
    }

    private long createBook(String title, int copies) {
        return idOf(
                post(
                        "/api/v1/books",
                        "{\"title\": \"%s\", \"totalCopies\": %d, \"authorId\": %d}"
                                .formatted(title, copies, authorId)));
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
