package com.batuhanmeral.library.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST and PUT requests. Holds only the fields a client may set. The size limits match the
 * column lengths in the categories table.
 */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
