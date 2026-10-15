package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookSearchCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;

/**
 * Gönderilen filtrelerden WHERE oluşturur; tek repository metodu her filtre kombinasyonuna yeter.
 */
final class BookSpecifications {

    /** LIKE desenindeki sonraki karakteri düz karakter yapar. */
    private static final char ESCAPE = '\\';

    private BookSpecifications() {}

    static Specification<Book> matching(BookSearchCriteria criteria) {
        List<PredicateSpecification<Book>> filters = new ArrayList<>();
        if (criteria.title() != null && !criteria.title().isBlank()) {
            filters.add(titleContains(criteria.title().strip()));
        }
        if (criteria.authorId() != null) {
            filters.add(hasAuthor(criteria.authorId()));
        }
        if (criteria.categoryId() != null) {
            filters.add(hasCategory(criteria.categoryId()));
        }
        if (criteria.available() != null) {
            filters.add(criteria.available() ? hasAvailableCopies() : hasNoAvailableCopies());
        }
        if (criteria.yearFrom() != null) {
            filters.add(publishedFrom(criteria.yearFrom()));
        }
        if (criteria.yearTo() != null) {
            filters.add(publishedUntil(criteria.yearTo()));
        }
        // Boş liste: WHERE yok, her kitap eşleşir.
        return Specification.where(PredicateSpecification.allOf(filters));
    }

    /**
     * Desen parametre olarak bağlanır, SQL metnine yazılmaz. Locale.ROOT ile küçültülür, sütun ise
     * veritabanının lower()'ı ile.
     */
    static PredicateSpecification<Book> titleContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (book, cb) -> cb.like(cb.lower(book.get("title")), pattern, ESCAPE);
    }

    static PredicateSpecification<Book> hasAuthor(Long authorId) {
        // author.id books tablosundaki author_id; join gerekmez.
        return (book, cb) -> cb.equal(book.get("author").get("id"), authorId);
    }

    static PredicateSpecification<Book> hasCategory(Long categoryId) {
        // book_categories ile inner join; sadece filtreler, kitap iki kez dönmez.
        return (book, cb) -> cb.equal(book.join("categories").get("id"), categoryId);
    }

    static PredicateSpecification<Book> hasAvailableCopies() {
        return (book, cb) -> cb.greaterThan(book.get("availableCopies"), 0);
    }

    static PredicateSpecification<Book> hasNoAvailableCopies() {
        return (book, cb) -> cb.equal(book.get("availableCopies"), 0);
    }

    static PredicateSpecification<Book> publishedFrom(int year) {
        return (book, cb) -> cb.greaterThanOrEqualTo(book.get("publishedYear"), year);
    }

    static PredicateSpecification<Book> publishedUntil(int year) {
        return (book, cb) -> cb.lessThanOrEqualTo(book.get("publishedYear"), year);
    }

    /** LIKE'ta % ve _ özel; "50%" arayan kullanıcı için bunlar (ve kaçış karakteri) kaçırılır. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
