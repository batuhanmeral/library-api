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
 * POST /books gövdesi. {@link BookRequest} gibi, ama ISBN varsa başlık boş bırakılabilir; eksikler
 * Open Library'den gelir.
 *
 * @param isbn ISBN-10 ya da ISBN-13, sadece rakam (ISBN-10 X ile bitebilir)
 * @param categoryIds boş olabilir; en fazla {@value BookRequest#MAX_CATEGORIES}
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

    /** Open Library bir şey ekleyebilir mi: ISBN var ve bir bilgi eksik. */
    public boolean canBeCompleted() {
        return isbn != null
                && (!hasTitle()
                        || description == null
                        || publishedYear == null
                        || pageCount == null);
    }
}
