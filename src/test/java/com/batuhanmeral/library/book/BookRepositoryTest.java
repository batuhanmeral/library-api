package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.config.JpaAuditingConfig;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(JpaAuditingConfig.class)
class BookRepositoryTest {

    @Autowired private TestEntityManager entityManager;

    @Autowired private EntityManagerFactory entityManagerFactory;

    @Autowired private BookRepository bookRepository;

    private Category fantasy;

    @BeforeEach
    void saveThreeBooksByThreeAuthors() {
        fantasy = entityManager.persist(new Category("Fantasy", null));
        for (String name : List.of("Le Guin", "Tolkien", "Pratchett")) {
            Author author = entityManager.persist(new Author(name));
            Book book = new Book(name + "'s book", 1);
            book.setAuthor(author);
            book.getCategories().add(fantasy);
            entityManager.persist(book);
        }
        entityManager.flush();
        // Start the test with an empty persistence context, as a new request would.
        entityManager.clear();
    }

    @Test
    void findAllLoadsBooksAuthorsAndCategoriesInOneQuery() {
        Statistics statistics = statistics();

        List<Book> books = bookRepository.findAll();
        books.forEach(book -> {
            book.getAuthor().getName();
            book.getCategories().size();
        });

        // Without the entity graph: 1 query for the books, then 1 per author and 1 per
        // category set (the N+1 problem): 7 queries.
        assertThat(books).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void findByAuthorIdReturnsOnlyThatAuthorsBooks() {
        Long tolkienId =
                entityManager
                        .getEntityManager()
                        .createQuery(
                                "select a.id from Author a where a.name = 'Tolkien'", Long.class)
                        .getSingleResult();

        assertThat(bookRepository.findByAuthorId(tolkienId))
                .extracting(Book::getTitle)
                .containsExactly("Tolkien's book");
    }

    @Test
    void existsByCategoriesIdTellsWhetherAnyBookUsesTheCategory() {
        Category unused = entityManager.persistAndFlush(new Category("Poetry", null));

        assertThat(bookRepository.existsByCategoriesId(fantasy.getId())).isTrue();
        assertThat(bookRepository.existsByCategoriesId(unused.getId())).isFalse();
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
