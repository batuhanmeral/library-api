package com.batuhanmeral.library.book.openlibrary;

/**
 * What Open Library knows about an edition, already checked: texts fit the book columns, the year
 * and the page count are plausible. A value that was missing or unusable is null.
 */
public record OpenLibraryBook(
        String title, String description, Integer publishedYear, Integer pageCount) {}
