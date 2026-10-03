package com.batuhanmeral.library.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.batuhanmeral.library.category.dto.CategoryRequest;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import com.batuhanmeral.library.common.error.DuplicateResourceException;
import com.batuhanmeral.library.common.error.ErrorCode;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        // No Spring container: the repository is a mock, the mapper is the generated class.
        categoryService = new CategoryService(categoryRepository, new CategoryMapperImpl());
    }

    @Test
    void createSavesANewCategoryWithTheGivenFields() {
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response =
                categoryService.create(new CategoryRequest("Science", "Popular science"));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getName()).isEqualTo("Science");
        assertThat(saved.getValue().getDescription()).isEqualTo("Popular science");
        assertThat(response.name()).isEqualTo("Science");
    }

    @Test
    void createRejectsANameThatIsAlreadyTaken() {
        when(categoryRepository.existsByName("Science")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Science", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("A category named 'Science' already exists.")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findByIdThrowsWhenTheCategoryDoesNotExist() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category with id 99 was not found.");
    }

    @Test
    void updateCopiesNameAndDescriptionToTheExistingCategory() {
        Category existing = new Category("Old name", "Old description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse result =
                categoryService.update(1L, new CategoryRequest("New name", "New description"));

        assertThat(result.name()).isEqualTo("New name");
        assertThat(existing.getName()).isEqualTo("New name");
        assertThat(existing.getDescription()).isEqualTo("New description");
    }

    @Test
    void updateThrowsWhenTheCategoryDoesNotExist() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, new CategoryRequest("Name", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateRejectsANameUsedByAnotherCategory() {
        Category existing = new Category("History", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameAndIdNot("Science", 1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Science", null)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(existing.getName()).isEqualTo("History");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteRemovesAnExistingCategory() {
        when(categoryRepository.existsById(1L)).thenReturn(true);

        categoryService.delete(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteThrowsWhenTheCategoryDoesNotExist() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).deleteById(any());
    }
}
