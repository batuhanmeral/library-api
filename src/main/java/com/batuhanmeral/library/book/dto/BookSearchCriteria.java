package com.batuhanmeral.library.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Optional filters of GET /books, bound from the query string ({@code ?title=hob&available=true}).
 * A filter that is not sent is null and does not restrict the result; the filters that are sent
 * must all match.
 *
 * @param title part of the title, case does not matter
 * @param available true: at least one copy can be borrowed; false: none can
 * @param yearFrom first publication year, inclusive
 * @param yearTo last publication year, inclusive
 */
public record BookSearchCriteria(
        @Size(max = 255) String title,
        @Positive Long authorId,
        @Positive Long categoryId,
        Boolean available,
        @Min(1) @Max(9999) Integer yearFrom,
        @Min(1) @Max(9999) Integer yearTo) {

    public static BookSearchCriteria byAuthor(Long authorId) {
        return new BookSearchCriteria(null, authorId, null, null, null, null);
    }
}
