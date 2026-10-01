package com.batuhanmeral.library.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        // No Spring container: the dependency is passed to the constructor by hand.
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void createSavesANewCategoryWithTheGivenFields() {
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category request = new Category("Science", "Popular science");
        categoryService.create(request);

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(request);
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getName()).isEqualTo("Science");
        assertThat(saved.getValue().getDescription()).isEqualTo("Popular science");
    }

    @Test
    void updateCopiesNameAndDescriptionToTheExistingCategory() {
        Category existing = new Category("Old name", "Old description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        Optional<Category> result =
                categoryService.update(1L, new Category("New name", "New description"));

        assertThat(result).containsSame(existing);
        assertThat(existing.getName()).isEqualTo("New name");
        assertThat(existing.getDescription()).isEqualTo("New description");
    }

    @Test
    void updateReturnsEmptyWhenTheCategoryDoesNotExist() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Category> result = categoryService.update(99L, new Category("Name", null));

        assertThat(result).isEmpty();
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteRemovesAnExistingCategory() {
        when(categoryRepository.existsById(1L)).thenReturn(true);

        assertThat(categoryService.delete(1L)).isTrue();
        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteReturnsFalseWhenTheCategoryDoesNotExist() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThat(categoryService.delete(99L)).isFalse();
        verify(categoryRepository, never()).deleteById(any());
    }
}
