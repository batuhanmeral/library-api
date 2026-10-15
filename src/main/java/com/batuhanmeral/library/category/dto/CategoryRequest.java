package com.batuhanmeral.library.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Kategori POST ve PUT gövdesi; uzunluk sınırları categories tablosuyla aynı. */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
