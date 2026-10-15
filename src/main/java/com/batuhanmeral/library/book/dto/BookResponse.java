package com.batuhanmeral.library.book.dto;

import java.time.Instant;
import java.util.List;

/** API'nin döndüğü kitap, yazar ve kategori özetiyle. */
public record BookResponse(
        Long id,
        String isbn,
        String title,
        String description,
        Integer publishedYear,
        Integer pageCount,
        int totalCopies,
        int availableCopies,
        AuthorSummary author,
        List<CategorySummary> categories,
        Instant createdAt,
        Instant updatedAt) {

    public record AuthorSummary(Long id, String name) {}

    public record CategorySummary(Long id, String name) {}
}
