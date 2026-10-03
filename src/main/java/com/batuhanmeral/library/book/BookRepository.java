package com.batuhanmeral.library.book;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every method that returns books also fetches their author and categories with the same query
 * ({@code @EntityGraph}). Without it, reading those lazy relations for N books runs N more queries
 * for the authors and N for the categories (the N+1 problem).
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    @Override
    @EntityGraph(attributePaths = {"author", "categories"})
    List<Book> findAll();

    @Override
    @EntityGraph(attributePaths = {"author", "categories"})
    Optional<Book> findById(Long id);

    @EntityGraph(attributePaths = {"author", "categories"})
    List<Book> findByAuthorId(Long authorId);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    boolean existsByAuthorId(Long authorId);

    boolean existsByCategoriesId(Long categoryId);
}
