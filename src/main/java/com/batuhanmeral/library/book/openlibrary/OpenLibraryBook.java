package com.batuhanmeral.library.book.openlibrary;

/**
 * Open Library'nin bir baskı hakkında bildikleri, kontrol edilmiş haliyle; kullanılamayan değer
 * null.
 */
public record OpenLibraryBook(
        String title, String description, Integer publishedYear, Integer pageCount) {}
