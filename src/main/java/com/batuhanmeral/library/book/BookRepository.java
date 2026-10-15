package com.batuhanmeral.library.book;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Kitap dönen metodlar yazarı aynı sorguda getirir ({@code @EntityGraph}), N+1 olmasın.
 *
 * <p>Sayfalı metodlar kategorileri getirmez: koleksiyon fetch'i LIMIT'i bozar. Kategoriler tek ek
 * sorguyla yüklenir ({@code @BatchSize}).
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /** İsteğe bağlı filtrelerle arama, bkz. {@link BookSpecifications}. */
    @Override
    @EntityGraph(attributePaths = "author")
    Page<Book> findAll(Specification<Book> specification, Pageable pageable);

    /** Tek kitap: kategorileri aynı sorguda getirmek burada güvenli. */
    @Override
    @EntityGraph(attributePaths = {"author", "categories"})
    Optional<Book> findById(Long id);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    boolean existsByAuthorId(Long authorId);

    boolean existsByCategoriesId(Long categoryId);
}
