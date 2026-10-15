package com.batuhanmeral.library.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * GET /books filtreleri; gönderilmeyen filtre null'dır ve sonucu kısıtlamaz.
 *
 * @param title başlığın bir parçası, büyük/küçük harf fark etmez
 * @param available true: en az bir kopya rafta; false: hiç yok
 * @param yearFrom ilk yayın yılı, dahil
 * @param yearTo son yayın yılı, dahil
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
