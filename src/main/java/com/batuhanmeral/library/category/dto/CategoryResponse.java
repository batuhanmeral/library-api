package com.batuhanmeral.library.category.dto;

import java.time.Instant;

/** A category as the API returns it. */
public record CategoryResponse(
        Long id, String name, String description, Instant createdAt, Instant updatedAt) {}
