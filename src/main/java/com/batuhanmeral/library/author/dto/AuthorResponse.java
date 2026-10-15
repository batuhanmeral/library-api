package com.batuhanmeral.library.author.dto;

import java.time.Instant;
import java.time.LocalDate;

/** API'nin döndüğü yazar, kitapları olmadan (bkz. GET /authors/{id}/books). */
public record AuthorResponse(
        Long id,
        String name,
        String biography,
        LocalDate birthDate,
        Instant createdAt,
        Instant updatedAt) {}
