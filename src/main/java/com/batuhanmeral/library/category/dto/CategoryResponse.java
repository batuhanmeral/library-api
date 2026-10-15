package com.batuhanmeral.library.category.dto;

import java.time.Instant;

/** API'nin döndüğü kategori. */
public record CategoryResponse(
        Long id, String name, String description, Instant createdAt, Instant updatedAt) {}
