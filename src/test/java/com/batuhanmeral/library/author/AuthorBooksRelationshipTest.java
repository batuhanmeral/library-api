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

/** Author.books, Book.author'ın ters tarafı; foreign key'i sadece Book.author yazar. */
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

        // Hibernate yazarken Author.books'u yok sayar; book.author hâlâ null.
        assertThatThrownBy(() -> entityManager.persistAndFlush(book))
                .hasMessageContaining("author");
    }

    @Test
    void theInverseSideIsNotUpdatedInMemory() {
        Author author = entityManager.persist(new Author("Ursula K. Le Guin"));
        Book book = new Book("A Wizard of Earthsea", 2);
        book.setAuthor(author);
        entityManager.persistAndFlush(book);

        // Satır veritabanında, ama bu persistence context'teki Author nesnesi önceden yüklendi ve
        // listesi boş.
        assertThat(author.getBooks()).isEmpty();
    }
}
