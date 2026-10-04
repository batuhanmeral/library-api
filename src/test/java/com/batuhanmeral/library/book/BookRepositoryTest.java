package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.book.dto.BookSearchCriteria;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
    void aPageOfBooksLoadsItsAuthorsAndCategoriesWithAFixedNumberOfQueries() {
        Statistics statistics = statistics();

        Page<Book> page =
                bookRepository.findAll(
                        Specification.unrestricted(), PageRequest.of(0, 2, Sort.by("title")));
        page.forEach(book -> {
            book.getAuthor().getName();
            book.getCategories().size();
        });

        // 1: the books of the page with their authors (entity graph, a join).
        // 2: the total count, because the page is full and there may be more rows.
        // 3: the categories of every book on the page at once (@BatchSize).
        // Without the entity graph and @BatchSize: 2 + 1 per author + 1 per book (N+1).
        assertThat(page.getContent())
                .extracting(Book::getTitle)
                .containsExactly("Le Guin's book", "Pratchett's book");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void findByIdLoadsTheBookItsAuthorAndItsCategoriesInOneQuery() {
        Long id = bookRepository.findAll().getFirst().getId();
        entityManager.clear();
        Statistics statistics = statistics();

        Book book = bookRepository.findById(id).orElseThrow();
        book.getAuthor().getName();
        book.getCategories().size();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void aFilteredPageUsesTheSameNumberOfQueries() {
        Statistics statistics = statistics();
        BookSearchCriteria criteria =
                new BookSearchCriteria("BOOK", null, fantasy.getId(), true, null, null);

        Page<Book> page =
                bookRepository.findAll(
                        BookSpecifications.matching(criteria),
                        PageRequest.of(0, 2, Sort.by("title")));
        page.forEach(book -> {
            book.getAuthor().getName();
            book.getCategories().size();
        });

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void theAuthorFilterReturnsOnlyThatAuthorsBooks() {
        Long tolkienId =
                entityManager
                        .getEntityManager()
                        .createQuery(
                                "select a.id from Author a where a.name = 'Tolkien'", Long.class)
                        .getSingleResult();

        assertThat(
                        bookRepository.findAll(
                                BookSpecifications.matching(BookSearchCriteria.byAuthor(tolkienId)),
                                PageRequest.of(0, 20)))
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
