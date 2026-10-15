package com.batuhanmeral.library.config;

/**
 * Cache adları. Tekrarlanan string yerine sabit: yazım hatası hiçbir silmenin ulaşmadığı yeni bir
 * cache açardı.
 */
public final class CacheNames {

    /** GET /books/{id}: kitap id'si başına bir BookResponse. */
    public static final String BOOKS = "books";

    /** GET /categories: listenin tamamı tek kayıt. */
    public static final String CATEGORIES = "categories";

    /** CATEGORIES'in tek kaydının anahtarı, SpEL string literal'i olarak. */
    public static final String ALL = "'all'";

    private CacheNames() {}
}
