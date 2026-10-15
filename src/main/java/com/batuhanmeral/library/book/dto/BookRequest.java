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
 * Kitap POST ve PUT gövdesi. İlişkiler id ile gelir; rafta kalan kopya sayısını service hesaplar.
 *
 * @param isbn ISBN-10 ya da ISBN-13, sadece rakam (ISBN-10 X ile bitebilir)
 * @param categoryIds boş olabilir; en fazla {@value #MAX_CATEGORIES}
 */
public record BookRequest(
        @Pattern(regexp = "\\d{9}[\\dX]|\\d{13}", message = "must be 10 or 13 digits") String isbn,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Min(1) @Max(9999) Integer publishedYear,
        @Positive Integer pageCount,
        @NotNull @PositiveOrZero Integer totalCopies,
        @NotNull Long authorId,
        @Size(max = MAX_CATEGORIES) Set<@NotNull Long> categoryIds) {

    /** Tek istek sınırsız sayıda satır yükletmesin. */
    public static final int MAX_CATEGORIES = 20;
}
