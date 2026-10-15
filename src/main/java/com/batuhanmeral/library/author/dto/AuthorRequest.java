package com.batuhanmeral.library.author.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Yazar POST ve PUT gövdesi; uzunluk sınırları authors tablosuyla aynı. */
public record AuthorRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String biography,
        @Past LocalDate birthDate) {}
