package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.config.JpaAuditingConfig;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import java.time.LocalDate;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class LoanMappingTest implements DatabaseTest {

    @Autowired private TestEntityManager entityManager;

    @Test
    void loanIsStoredWithItsBookMemberAndStatusAsText() {
        Long loanId = saveLoan();

        Loan loan = entityManager.find(Loan.class, loanId);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(loan.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(loan.getCreatedAt()).isNotNull();
        Object storedStatus =
                entityManager
                        .getEntityManager()
                        .createNativeQuery("select status from loans where id = :id")
                        .setParameter("id", loanId)
                        .getSingleResult();
        assertThat(storedStatus).isEqualTo("ACTIVE");
    }

    @Test
    void manyToOneRelationshipsAreLoadedLazily() {
        Long loanId = saveLoan();

        Loan loan = entityManager.find(Loan.class, loanId);

        // Only proxies holding the ids; no query has run for the book or the member yet.
        assertThat(Hibernate.isInitialized(loan.getBook())).isFalse();
        assertThat(Hibernate.isInitialized(loan.getMember())).isFalse();
        assertThat(loan.getBook().getTitle()).isEqualTo("Dune");
        assertThat(Hibernate.isInitialized(loan.getBook())).isTrue();
    }

    @Test
    void userRoleIsStoredAsTextAndTimestampsAreFilled() {
        User user = entityManager.persistFlushFind(member());

        assertThat(user.getRole()).isEqualTo(Role.MEMBER);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    private Long saveLoan() {
        Author author = entityManager.persist(new Author("Frank Herbert"));
        Book book = new Book("Dune", 1);
        book.setAuthor(author);
        entityManager.persist(book);
        User member = entityManager.persist(member());
        Loan loan = entityManager.persist(new Loan(book, member, LocalDate.of(2026, 12, 1), 14));
        entityManager.flush();
        entityManager.clear();
        return loan.getId();
    }

    private static User member() {
        return new User("reader@example.com", "{noop}not-a-real-hash", "Ada Reader", Role.MEMBER);
    }
}
