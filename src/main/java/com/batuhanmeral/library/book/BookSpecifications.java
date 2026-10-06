package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookSearchCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the WHERE clause of a book search from the filters that were sent. Each filter is a small
 * predicate; {@link #matching} joins the ones that apply with AND. One repository method serves
 * every combination of filters, where derived queries would need one method per combination.
 */
final class BookSpecifications {

    /** Marks the next character of a LIKE pattern as a plain character. */
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
        // allOf of an empty list is unrestricted(): no WHERE clause, every book matches.
        return Specification.where(PredicateSpecification.allOf(filters));
    }

    /**
     * The pattern is passed as a plain String, so Hibernate binds it as a parameter ({@code like ?
     * escape '\'}); {@code cb.literal(...)} would write it into the SQL text. It is lowercased here
     * with fixed rules (Locale.ROOT), the column with the database's lower().
     */
    static PredicateSpecification<Book> titleContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (book, cb) -> cb.like(cb.lower(book.get("title")), pattern, ESCAPE);
    }

    static PredicateSpecification<Book> hasAuthor(Long authorId) {
        // author.id is the author_id column of books; no join is needed for it.
        return (book, cb) -> cb.equal(book.get("author").get("id"), authorId);
    }

    static PredicateSpecification<Book> hasCategory(Long categoryId) {
        // An inner join on book_categories. A book has a category at most once, so it is not
        // returned twice. The join only filters: Book.categories is loaded separately, in full.
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

    /**
     * In LIKE, % matches any text and _ any single character. A user who searches for "50%" means
     * the characters 5, 0 and %, so those two (and the escape character itself) are escaped.
     */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
