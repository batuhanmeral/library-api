package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two members borrow the last copy of a book at the same moment. Both requests must read the book
 * before either commits, or the second one simply sees no copy left. Each borrow therefore runs
 * inside an outer transaction (LoanService.borrow joins it, propagation REQUIRED) that waits at a
 * barrier until both have read and changed the book, and only then commits.
 */
@SpringBootTest
class LoanConcurrencyTest {

    @Autowired private LoanService loanService;

    @Autowired private BookRepository bookRepository;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

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

    private long saveBook(String title) {
        Book book = new Book(title, 1);
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
