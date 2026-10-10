package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.MutableClock;
import com.batuhanmeral.library.MutableClockConfig;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.common.logging.RequestIdFilter;
import com.batuhanmeral.library.notification.SmsNotificationService;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * POST /admin/jobs/overdue-check answers at once and runs the job on another thread. The test
 * cannot know when that thread is done, so it polls the database with Awaitility until the result
 * shows up (or a timeout fails the test) instead of sleeping for a guessed time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
class OverdueCheckApiTest implements DatabaseTest {

    private static final String OVERDUE_CHECK = "/api/v1/admin/jobs/overdue-check";

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private OverdueLoanJob job;

    @Autowired private MutableClock clock;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private LoanRepository loanRepository;

    @MockitoSpyBean private SmsNotificationService sms;

    private long loanId;

    @BeforeEach
    void saveALoanThatIsOverdue() {
        clock.setInstant(Instant.now());
        Author author = authorRepository.save(new Author("Frank Herbert"));
        Book book = new Book("Dune", 1);
        book.setAuthor(author);
        bookRepository.save(book);
        User member = new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER);
        member.setPhone("+15555550142");
        userRepository.save(member);
        loanId = loanRepository.save(new Loan(book, member, LocalDate.now(clock), 14)).getId();
        clock.advance(Duration.ofDays(15));
    }

    @Test
    void theCheckStartsInTheBackgroundAndTheResponseIs202() {
        AtomicReference<String> smsThread = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            smsThread.set(Thread.currentThread().getName());
                            return invocation.callRealMethod();
                        })
                .when(sms)
                .send(any(), anyString());

        MvcTestResult result =
                mvc.post()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(OVERDUE_CHECK)
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(statusOfTheLoan()).isEqualTo("OVERDUE"));
        await().atMost(Duration.ofSeconds(10)).until(() -> !job.isRunning());
        // The job ran on a thread of the task executor (spring.task.execution), not this one.
        assertThat(smsThread.get()).startsWith("library-task-");
    }

    /**
     * The job runs on another thread; the request id travels with it (MdcTaskDecorator), so its log
     * lines can be found by the id the response returned.
     */
    @Test
    void theBackgroundJobLogsWithTheRequestIdOfTheRequestThatStartedIt() {
        AtomicReference<String> requestIdInTheJob = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            requestIdInTheJob.set(MDC.get(RequestIdFilter.MDC_KEY));
                            return invocation.callRealMethod();
                        })
                .when(sms)
                .send(any(), anyString());

        MvcTestResult result =
                mvc.post()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(OVERDUE_CHECK)
                        .header(RequestIdFilter.HEADER, "overdue-check-1")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(10)).until(() -> requestIdInTheJob.get() != null);
        await().atMost(Duration.ofSeconds(10)).until(() -> !job.isRunning());
        assertThat(requestIdInTheJob).hasValue("overdue-check-1");
    }

    @Test
    void aSecondStartWhileTheCheckIsRunningIs409() {
        assertThat(job.tryClaim()).isTrue();
        try {
            MvcTestResult result =
                    mvc.post()
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                            .uri(OVERDUE_CHECK)
                            .exchange();

            assertThat(result)
                    .hasStatus(HttpStatus.CONFLICT)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errorCode")
                    .isEqualTo("JOB_ALREADY_RUNNING");
            assertThat(statusOfTheLoan()).isEqualTo("ACTIVE");
        } finally {
            job.release();
        }
    }

    private String statusOfTheLoan() {
        return jdbcTemplate.queryForObject(
                "select status from loans where id = ?", String.class, loanId);
    }
}
