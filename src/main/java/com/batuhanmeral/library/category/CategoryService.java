package com.batuhanmeral.library.category;

import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.category.dto.CategoryRequest;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import com.batuhanmeral.library.common.error.DuplicateResourceException;
import com.batuhanmeral.library.common.error.ResourceInUseException;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final CategoryMapper categoryMapper;

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(categoryMapper::toResponse).toList();
    }

    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(getCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw nameTaken(request.name());
        }
        Category saved = categoryRepository.save(categoryMapper.toEntity(request));
        return categoryMapper.toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw nameTaken(request.name());
        }
        categoryMapper.updateEntity(request, category);
        // The entity is managed, so the change is saved without save() (dirty checking). Flush
        // now so that the UPDATE runs and updatedAt is set before the response is built.
        categoryRepository.flush();
        return categoryMapper.toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category", id);
        }
        if (bookRepository.existsByCategoriesId(id)) {
            throw new ResourceInUseException(
                    "Category with id %d is used by books and cannot be deleted.".formatted(id));
        }
        categoryRepository.deleteById(id);
    }

    private Category getCategory(Long id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private static DuplicateResourceException nameTaken(String name) {
        return new DuplicateResourceException(
                "A category named '%s' already exists.".formatted(name));
    }
}
