package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * İki transaction aynı kitabı değiştirir; @Version olmasa ikinci commit ilkinin değişikliğini
 * ezerdi (lost update). Transaction'lar gerçekten commit edilir.
 */
@SpringBootTest
class BookOptimisticLockingTest implements DatabaseTest {

    @Autowired private BookRepository bookRepository;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

    private long bookId;

    @BeforeEach
    void saveABookWithTwoCopies() {
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
                            // A transaction'ı kitabı okur: version 0, 2 kopya.
                            Book seenByA = bookRepository.findById(bookId).orElseThrow();

                            // B transaction'ı (REQUIRES_NEW) bir kopya alıp commit eder: version 1,
                            // 1 kopya.
                            inNewTransaction(() ->
                                    bookRepository.findById(bookId).orElseThrow().borrowCopy());

                            // A hâlâ 2 kopya görür ve bir tane alır; UPDATE'i "version = 0" ister
                            // ve hiçbir satırı değiştirmez.
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
