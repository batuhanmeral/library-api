package com.batuhanmeral.library.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Body of POST /books. Like {@link BookRequest}, except that the title may be left out when an ISBN
 * is given: the missing details then come from Open Library. PUT keeps using BookRequest, so
 * replacing a book still needs a title.
 *
 * @param isbn ISBN-10 or ISBN-13, digits only (an ISBN-10 may end with X)
 * @param categoryIds may be omitted or empty; at most {@value BookRequest#MAX_CATEGORIES}
 */
@TitleRequiredWithoutIsbn
public record NewBookRequest(
        @Pattern(regexp = "\\d{9}[\\dX]|\\d{13}", message = "must be 10 or 13 digits") String isbn,
        @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Min(1) @Max(9999) Integer publishedYear,
        @Positive Integer pageCount,
        @NotNull @PositiveOrZero Integer totalCopies,
        @NotNull Long authorId,
        @Size(max = BookRequest.MAX_CATEGORIES) Set<@NotNull Long> categoryIds) {

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }

    /** Whether Open Library could add anything: an ISBN is given and a detail is missing. */
    public boolean canBeCompleted() {
        return isbn != null
                && (!hasTitle()
                        || description == null
                        || publishedYear == null
                        || pageCount == null);
    }
}
