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
 * PATCH /books/{id} gövdesi. Her alan isteğe bağlı: gelmeyen alan değişmez, bu yolla alan silinemez
 * (PUT kullan). Gelen alanlar {@link BookRequest} kurallarına uyar.
 *
 * @param categoryIds gelirse tüm kategorilerin yerine geçer; boş liste hepsini kaldırır
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
