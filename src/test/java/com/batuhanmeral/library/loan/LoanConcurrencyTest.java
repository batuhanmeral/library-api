package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Members borrow the same book at the same moment. All requests must read the book before any of
 * them commits, or the later ones simply see the earlier loans. The first two tests call
 * LoanService inside an outer transaction (LoanService.borrow joins it, propagation REQUIRED) that
 * waits at a barrier. The tests of LoanFacade cannot wrap it in a transaction (each retry needs a
 * new one), so the barrier sits in LoanMapper.toResponse, which LoanService calls inside its
 * transaction after it has read and changed the book and before it commits.
 */
@SpringBootTest
class LoanConcurrencyTest {

    @Autowired private LoanService loanService;

    @Autowired private BookRepository bookRepository;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private LoanFacade loanFacade;

    @MockitoSpyBean private LoanMapper loanMapper;

    private long bookId;
    private long firstMember;
    private long secondMember;

    @BeforeEach
    void saveTheLastCopyAndTwoMembers() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate,
                "loans",
                "book_categories",
                "books",
                "authors",
                "categories",
                "users");
        Author author = authorRepository.save(new Author("Frank Herbert"));
        Book book = new Book("Dune", 1);
        book.setAuthor(author);
        bookId = bookRepository.save(book).getId();
        firstMember = saveMember("ada@example.com");
        secondMember = saveMember("grace@example.com");
    }

    @Test
    void onlyOneOfTwoConcurrentBorrowsOfTheLastCopySucceeds() throws Exception {
        CyclicBarrier bothHaveRead = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(borrowAndWait(firstMember, bothHaveRead));
            Future<String> second = executor.submit(borrowAndWait(secondMember, bothHaveRead));

            List<String> outcomes =
                    List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes)
                    .containsExactlyInAnyOrder("borrowed", "OptimisticLockingFailureException");
        } finally {
            executor.shutdownNow();
        }
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "loans")).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select available_copies from books where id = ?",
                                Integer.class,
                                bookId))
                .isZero();
    }

    @Test
    void concurrentBorrowsOfDifferentBooksCannotPassTheLoanLimit() throws Exception {
        // Two open loans; the limit is 3. Different books, so Book.version does not help here.
        loanService.borrow(new LoanRequest(saveBook("Dune Messiah"), firstMember));
        loanService.borrow(new LoanRequest(saveBook("Children of Dune"), firstMember));
        long third = saveBook("God Emperor of Dune");
        long fourth = saveBook("Heretics of Dune");
        CountDownLatch firstHasBorrowed = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // A borrows and keeps its transaction open for a moment; B borrows meanwhile.
            // Without a lock B counts 2 open loans (A has not committed) and the member ends
            // up with 4.
            Future<String> a =
                    executor.submit(() -> {
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(status -> {
                                    loanService.borrow(new LoanRequest(third, firstMember));
                                    firstHasBorrowed.countDown();
                                    sleep(1000);
                                });
                        return "borrowed";
                    });
            Future<String> b =
                    executor.submit(() -> {
                        firstHasBorrowed.await(10, TimeUnit.SECONDS);
                        try {
                            loanService.borrow(new LoanRequest(fourth, firstMember));
                            return "borrowed";
                        } catch (BusinessRuleException e) {
                            return e.getErrorCode().name();
                        }
                    });

            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo("borrowed");
            assertThat(b.get(30, TimeUnit.SECONDS)).isEqualTo("LOAN_LIMIT_REACHED");
        } finally {
            executor.shutdownNow();
        }
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "loans")).isEqualTo(3);
    }

    @Test
    void withRetriesFiveConcurrentBorrowsOfFiveCopiesAllSucceed() throws Exception {
        long book = saveBook("Dune Messiah", 5);
        List<Long> members = saveMembers(5);

        BorrowRace race = borrowAtOnce(book, members);

        assertThat(race.outcomes()).hasSize(5).containsOnly("borrowed");
        // More than one pass through the mapper per member: some borrows lost a race and ran again.
        assertThat(race.attempts()).isGreaterThan(5);
        assertThat(availableCopies(book)).isZero();
    }

    @Test
    void withRetriesTheLosersOfTheLastCopyGetBookNotAvailable() throws Exception {
        long book = saveBook("Dune Messiah", 1);
        List<Long> members = saveMembers(3);

        BorrowRace race = borrowAtOnce(book, members);

        assertThat(race.outcomes())
                .containsExactlyInAnyOrder("borrowed", "BOOK_NOT_AVAILABLE", "BOOK_NOT_AVAILABLE");
        assertThat(availableCopies(book)).isZero();
    }

    private record BorrowRace(List<String> outcomes, int attempts) {}

    /**
     * Every member borrows the book through LoanFacade at the same time. The first attempt of each
     * waits in the mapper until all have read the book, so all but one lose the race at commit.
     */
    private BorrowRace borrowAtOnce(long bookId, List<Long> memberIds) throws Exception {
        int count = memberIds.size();
        CyclicBarrier allHaveRead = new CyclicBarrier(count);
        AtomicInteger mapperCalls = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (mapperCalls.getAndIncrement() < count) {
                                await(allHaveRead);
                            }
                            return invocation.callRealMethod();
                        })
                .when(loanMapper)
                .toResponse(any());
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                futures.add(
                        executor.submit(() -> {
                            try {
                                loanFacade.borrow(new LoanRequest(bookId, memberId));
                                return "borrowed";
                            } catch (BusinessRuleException e) {
                                return e.getErrorCode().name();
                            } catch (OptimisticLockingFailureException e) {
                                return "OptimisticLockingFailureException";
                            }
                        }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return new BorrowRace(outcomes, mapperCalls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(saveMember("reader" + i + "@example.com"));
        }
        return ids;
    }

    private int availableCopies(long bookId) {
        return jdbcTemplate.queryForObject(
                "select available_copies from books where id = ?", Integer.class, bookId);
    }

    private long saveBook(String title) {
        return saveBook(title, 1);
    }

    private long saveBook(String title, int copies) {
        Book book = new Book(title, copies);
        book.setAuthor(authorRepository.findAll().getFirst());
        return bookRepository.save(book).getId();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Callable<String> borrowAndWait(long memberId, CyclicBarrier barrier) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return () -> {
            try {
                transaction.executeWithoutResult(status -> {
                    loanService.borrow(new LoanRequest(bookId, memberId));
                    await(barrier);
                });
                return "borrowed";
            } catch (OptimisticLockingFailureException e) {
                return "OptimisticLockingFailureException";
            }
        };
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long saveMember(String email) {
        return userRepository
                .save(new User(email, "{noop}not-a-real-hash", "Reader", Role.MEMBER))
                .getId();
    }
}
