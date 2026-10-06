package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two transactions change the same book. Without @Version the second commit would overwrite the
 * first one's change (a lost update). Each transaction here is real and commits, so this test does
 * not use @DataJpaTest's single rolled-back transaction.
 */
@SpringBootTest
class BookOptimisticLockingTest {

    @Autowired private BookRepository bookRepository;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

    private long bookId;

    @BeforeEach
    void saveABookWithTwoCopies() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate, "loans", "book_categories", "books", "authors", "categories");
        Author author = authorRepository.save(new Author("Frank Herbert"));
        Book book = new Book("Dune", 2);
        book.setAuthor(author);
        bookId = bookRepository.save(book).getId();
    }

    @Test
    void aNewBookStartsAtVersionZeroAndEachUpdateIncrementsIt() {
        assertThat(bookRepository.findById(bookId).orElseThrow().getVersion()).isZero();

        inNewTransaction(() -> bookRepository.findById(bookId).orElseThrow().borrowCopy());

        assertThat(bookRepository.findById(bookId).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void theSecondOfTwoConcurrentChangesFailsInsteadOfOverwritingTheFirst() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                        outer.executeWithoutResult(status -> {
                            // Transaction A reads the book: version 0, 2 copies available.
                            Book seenByA = bookRepository.findById(bookId).orElseThrow();

                            // Transaction B (a separate one, REQUIRES_NEW) borrows a copy and
                            // commits: version 1, 1 copy available.
                            inNewTransaction(() ->
                                    bookRepository.findById(bookId).orElseThrow().borrowCopy());

                            // A still sees 2 copies and borrows one too. Its UPDATE says
                            // "where id = ? and version = 0" and changes no row.
                            seenByA.borrowCopy();
                        }))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Integer available =
                jdbcTemplate.queryForObject(
                        "select available_copies from books where id = ?", Integer.class, bookId);
        assertThat(available).isEqualTo(1);
    }

    private void inNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }
}
