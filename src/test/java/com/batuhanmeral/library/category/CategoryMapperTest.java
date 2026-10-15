package com.batuhanmeral.library.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.category.dto.CategoryRequest;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoryMapperTest {

    // Implementation'ı MapStruct derlemede üretir.
    private final CategoryMapper mapper = new CategoryMapperImpl();

    @Test
    void toEntityCopiesTheRequestFieldsAndLeavesTheIdEmpty() {
        Category category = mapper.toEntity(new CategoryRequest("Science", "Popular science"));

        assertThat(category.getId()).isNull();
        assertThat(category.getName()).isEqualTo("Science");
        assertThat(category.getDescription()).isEqualTo("Popular science");
    }

    @Test
    void toResponseCopiesAllFieldsIncludingTheIdAndTheTimestamps() {
        Category category = new Category("Science", "Popular science");
        Instant createdAt = Instant.parse("2026-11-24T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-24T11:00:00Z");
        ReflectionTestUtils.setField(category, "id", 7L);
        ReflectionTestUtils.setField(category, "createdAt", createdAt);
        ReflectionTestUtils.setField(category, "updatedAt", updatedAt);

        CategoryResponse response = mapper.toResponse(category);

        assertThat(response)
                .isEqualTo(
                        new CategoryResponse(
                                7L, "Science", "Popular science", createdAt, updatedAt));
    }

    @Test
    void updateEntityOverwritesTheFieldsButKeepsTheId() {
        Category category = new Category("Old name", "Old description");
        ReflectionTestUtils.setField(category, "id", 7L);

        mapper.updateEntity(new CategoryRequest("New name", null), category);

        assertThat(category.getId()).isEqualTo(7L);
        assertThat(category.getName()).isEqualTo("New name");
        assertThat(category.getDescription()).isNull();
    }
}
