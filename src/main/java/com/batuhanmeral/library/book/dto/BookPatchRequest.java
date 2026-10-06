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
 * Body of PATCH /books/{id}. Every field is optional: a field that is missing or null leaves the
 * book unchanged, so a field cannot be cleared this way (use PUT for that). The rules for the
 * fields that are sent are those of {@link BookRequest}; a null value passes every constraint here,
 * so "required" becomes "not blank if sent".
 *
 * @param categoryIds replaces all categories when sent; an empty list removes them all
 */
public record BookPatchRequest(
        @Pattern(regexp = "\\d{9}[\\dX]|\\d{13}", message = "must be 10 or 13 digits") String isbn,
        @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
                String title,
        @Size(max = 2000) String description,
        @Min(1) @Max(9999) Integer publishedYear,
        @Positive Integer pageCount,
        @PositiveOrZero Integer totalCopies,
        Long authorId,
        @Size(max = BookRequest.MAX_CATEGORIES) Set<@NotNull Long> categoryIds) {}
