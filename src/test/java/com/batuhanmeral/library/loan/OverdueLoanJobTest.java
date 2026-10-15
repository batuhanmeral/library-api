package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.MutableClock;
import com.batuhanmeral.library.MutableClockConfig;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.notification.EmailNotificationService;
import com.batuhanmeral.library.notification.Recipient;
import com.batuhanmeral.library.notification.SmsNotificationService;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Gece işi, testin ileri aldığı saatle elle çalıştırılır. Grup boyu 2: beş üye üç grup (üç
 * transaction) eder.
 */
@SpringBootTest(properties = "library.loan.overdue-check.batch-size=2")
@Import(MutableClockConfig.class)
class OverdueLoanJobTest implements DatabaseTest {

    @Autowired private OverdueLoanJob job;

    @Autowired private OverdueLoanScheduler scheduler;

    @Autowired private MutableClock clock;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private UserRepository userRepository;

    @MockitoSpyBean private LoanRepository loanRepository;

    @MockitoSpyBean private SmsNotificationService sms;

    @MockitoSpyBean private EmailNotificationService email;

    private Author author;

    @BeforeEach
    void cleanTablesAndResetTheClock() {
        clock.setInstant(Instant.now());
        author = authorRepository.save(new Author("Frank Herbert"));
    }

    @Test
    void loansPastTheirDueDateBecomeOverdueAndEachMemberGetsOneSms() {
        User ada = saveMember("ada@example.com", "+15555550142");
        User grace = saveMember("grace@example.com", "+15555550143");
        User alan = saveMember("alan@example.com", null);
        long adasFirst = lend(ada, "Dune");
        long adasSecond = lend(ada, "Dune Messiah");
        long gracesLoan = lend(grace, "Children of Dune");
        long alansLoan = lend(alan, "God Emperor of Dune");
        clock.advance(Duration.ofDays(15));

        OverdueCheckResult result = job.run();

        assertThat(result.loansMarked()).isEqualTo(4);
        assertThat(result.remindersSent()).isEqualTo(2);
        for (long loan : new long[] {adasFirst, adasSecond, gracesLoan, alansLoan}) {
            assertThat(statusOf(loan)).isEqualTo(LoanStatus.OVERDUE);
        }
        verify(sms)
                .send(eq(new Recipient("ada@example.com", "+15555550142")), contains("2 overdue"));
        verify(sms)
                .send(
                        eq(new Recipient("grace@example.com", "+15555550143")),
                        contains("1 overdue"));
        verify(sms, times(2)).send(any(), anyString());
        // Hatırlatmalar sadece SMS ile gider, @Primary e-posta ile değil.
        verify(email, never()).send(any(), anyString());
    }

    @Test
    void aLoanDueTodayAndAReturnedLoanAreLeftAlone() {
        User ada = saveMember("ada@example.com", "+15555550142");
        long dueToday = lend(ada, "Dune");
        long returned = lend(ada, "Dune Messiah");
        Loan loan = loanRepository.findById(returned).orElseThrow();
        loan.markReturned(LocalDate.now(clock));
        loanRepository.save(loan);
        clock.advance(Duration.ofDays(14));

        OverdueCheckResult result = job.run();

        assertThat(result.loansMarked()).isZero();
        assertThat(statusOf(dueToday)).isEqualTo(LoanStatus.ACTIVE);
        assertThat(statusOf(returned)).isEqualTo(LoanStatus.RETURNED);
        verify(sms, never()).send(any(), anyString());
    }

    @Test
    void runningTheJobAgainChangesNothingAndSendsNothing() {
        lend(saveMember("ada@example.com", "+15555550142"), "Dune");
        clock.advance(Duration.ofDays(15));
        job.run();

        OverdueCheckResult second = job.run();

        assertThat(second.loansMarked()).isZero();
        assertThat(second.remindersSent()).isZero();
        verify(sms, times(1)).send(any(), anyString());
    }

    @Test
    void manyMembersAreHandledBatchByBatch() {
        for (int i = 0; i < 5; i++) {
            lend(saveMember("reader" + i + "@example.com", "+1555555010" + i), "Book " + i);
        }
        clock.advance(Duration.ofDays(15));

        OverdueCheckResult result = job.run();

        assertThat(result.loansMarked()).isEqualTo(5);
        assertThat(result.remindersSent()).isEqualTo(5);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from loans where status = 'OVERDUE'",
                                Integer.class))
                .isEqualTo(5);
    }

    @Test
    void aBatchWhoseLoansWereReturnedMeanwhileDoesNotEndTheRun() {
        for (int i = 0; i < 5; i++) {
            lend(saveMember("reader" + i + "@example.com", "+1555555010" + i), "Book " + i);
        }
        clock.advance(Duration.ofDays(15));
        // İlk grup üye bulur ama ödünçler kilitlenene kadar iade edilmiştir; çalışma sonraki grupla
        // devam etmeli. Spy varsayılan cevabıyla gerçek proxy'ye gider.
        Answer<?> real =
                mockingDetails(loanRepository).getMockCreationSettings().getDefaultAnswer();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> calls.getAndIncrement() == 0 ? List.of() : real.answer(invocation))
                .when(loanRepository)
                .findLockedByMemberIdInAndStatusAndDueDateBefore(anyCollection(), any(), any());

        OverdueCheckResult result = job.run();

        assertThat(result.loansMarked()).isEqualTo(5);
    }

    @Test
    void theScheduledRunIsSkippedWhileTheJobIsRunning() {
        long loan = lend(saveMember("ada@example.com", "+15555550142"), "Dune");
        clock.advance(Duration.ofDays(15));
        assertThat(job.tryClaim()).isTrue();
        try {
            scheduler.markOverdueLoans();

            assertThat(statusOf(loan)).isEqualTo(LoanStatus.ACTIVE);
        } finally {
            job.release();
        }
        scheduler.markOverdueLoans();
        assertThat(statusOf(loan)).isEqualTo(LoanStatus.OVERDUE);
    }

    private User saveMember(String emailAddress, String phone) {
        User member = new User(emailAddress, "{noop}x", "Reader", Role.MEMBER);
        member.setPhone(phone);
        return userRepository.save(member);
    }

    /** Test saatine göre bugün yapılan, 14 gün sonra dolan ödünç. */
    private long lend(User member, String title) {
        Book book = new Book(title, 1);
        book.setAuthor(author);
        bookRepository.save(book);
        return loanRepository.save(new Loan(book, member, LocalDate.now(clock), 14)).getId();
    }

    private LoanStatus statusOf(long loanId) {
        return LoanStatus.valueOf(
                jdbcTemplate.queryForObject(
                        "select status from loans where id = ?", String.class, loanId));
    }
}
