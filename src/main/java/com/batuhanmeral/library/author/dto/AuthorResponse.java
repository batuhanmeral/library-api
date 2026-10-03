package com.batuhanmeral.library.author.dto;

import java.time.Instant;
import java.time.LocalDate;

/** An author as the API returns it, without the books (see GET /authors/{id}/books). */
public record AuthorResponse(
        Long id,
        String name,
        String biography,
        LocalDate birthDate,
        Instant createdAt,
        Instant updatedAt) {}
