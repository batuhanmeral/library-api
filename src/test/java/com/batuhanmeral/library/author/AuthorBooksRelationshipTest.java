package com.batuhanmeral.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Author.books is the inverse side of Book.author; only Book.author writes the foreign key. */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class AuthorBooksRelationshipTest implements DatabaseTest {

    @Autowired private TestEntityManager entityManager;

    @Test
    void settingTheOwningSideStoresTheRelationship() {
        Author author = entityManager.persist(new Author("Ursula K. Le Guin"));
        Book book = new Book("A Wizard of Earthsea", 2);
        book.setAuthor(author);
        entityManager.persist(book);
        entityManager.flush();
        entityManager.clear();

        Author loaded = entityManager.find(Author.class, author.getId());

        assertThat(loaded.getBooks())
                .extracting(Book::getTitle)
                .containsExactly("A Wizard of Earthsea");
    }

    @Test
    void addingOnlyToTheInverseSideDoesNotSetTheForeignKey() {
        Author author = entityManager.persist(new Author("Ursula K. Le Guin"));
        Book book = new Book("A Wizard of Earthsea", 2);
        author.getBooks().add(book);

        // Hibernate ignores Author.books when writing; book.author is still null.
        assertThatThrownBy(() -> entityManager.persistAndFlush(book))
                .hasMessageContaining("author");
    }

    @Test
    void theInverseSideIsNotUpdatedInMemory() {
        Author author = entityManager.persist(new Author("Ursula K. Le Guin"));
        Book book = new Book("A Wizard of Earthsea", 2);
        book.setAuthor(author);
        entityManager.persistAndFlush(book);

        // The row is in the database, but the Author object in this persistence context was
        // loaded before and still has an empty list.
        assertThat(author.getBooks()).isEmpty();
    }
}
