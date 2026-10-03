package com.batuhanmeral.library.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Body of POST and PUT requests for books. Relations are sent as ids; the service loads the
 * entities. The available copies are not sent: the service derives them from the total.
 *
 * @param isbn ISBN-10 or ISBN-13, digits only (an ISBN-10 may end with X)
 * @param categoryIds may be omitted or empty
 */
public record BookRequest(
        @Pattern(regexp = "\\d{9}[\\dX]|\\d{13}", message = "must be 10 or 13 digits") String isbn,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Min(1) @Max(9999) Integer publishedYear,
        @Positive Integer pageCount,
        @NotNull @PositiveOrZero Integer totalCopies,
        @NotNull Long authorId,
        Set<@NotNull Long> categoryIds) {}
