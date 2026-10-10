package com.batuhanmeral.library.config;

/**
 * Names of the caches, used in the {@code @Cacheable} and {@code @CacheEvict} annotations and in
 * CacheConfig. Constants instead of strings repeated in many places: a typo would create a cache of
 * its own, which no eviction ever reaches.
 */
public final class CacheNames {

    /** GET /books/{id}: one BookResponse per book id. */
    public static final String BOOKS = "books";

    /** GET /categories: the whole list as one entry. */
    public static final String CATEGORIES = "categories";

    /** The key of the one entry of CATEGORIES, as a SpEL expression (a string literal). */
    public static final String ALL = "'all'";

    private CacheNames() {}
}
