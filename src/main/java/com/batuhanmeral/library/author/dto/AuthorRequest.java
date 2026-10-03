package com.batuhanmeral.library.author.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of POST and PUT requests for authors. The size limits match the authors table. */
public record AuthorRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String biography,
        @Past LocalDate birthDate) {}
