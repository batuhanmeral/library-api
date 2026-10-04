package com.batuhanmeral.library.book;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Methods that return books fetch their author with the same query ({@code @EntityGraph}). Without
 * it, reading the lazy author of N books runs N more queries (the N+1 problem).
 *
 * <p>Paged methods do not fetch the categories. A collection fetch in a paged query returns one row
 * per book and category, so LIMIT cannot count books. Hibernate 7 then pages in a subquery when it
 * can; when it cannot (for example when sorting by a category), it loads every matching row and
 * cuts the page in memory. The categories of a page are loaded in one extra query instead, see
 * {@code @BatchSize} on {@link Book#getCategories()}.
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /** Search with optional filters, see {@link BookSpecifications}. */
    @Override
    @EntityGraph(attributePaths = "author")
    Page<Book> findAll(Specification<Book> specification, Pageable pageable);

    /** A single book: fetching its categories in the same query is safe here. */
    @Override
    @EntityGraph(attributePaths = {"author", "categories"})
    Optional<Book> findById(Long id);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    boolean existsByAuthorId(Long authorId);

    boolean existsByCategoriesId(Long categoryId);
}
