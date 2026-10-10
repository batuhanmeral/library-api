package com.batuhanmeral.library.category;

import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.category.dto.CategoryRequest;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import com.batuhanmeral.library.common.audit.AuditAction;
import com.batuhanmeral.library.common.audit.Auditable;
import com.batuhanmeral.library.common.error.DuplicateResourceException;
import com.batuhanmeral.library.common.error.ResourceInUseException;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import com.batuhanmeral.library.config.CacheNames;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final CategoryMapper categoryMapper;

    /** The whole list is one entry; any change of a category drops it. */
    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = CacheNames.ALL)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(categoryMapper::toResponse).toList();
    }

    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(getCategory(id));
    }

    @Auditable(action = AuditAction.CATEGORY_CREATE, target = "#result?.id()")
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, key = CacheNames.ALL)
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw nameTaken(request.name());
        }
        Category saved = categoryRepository.save(categoryMapper.toEntity(request));
        return categoryMapper.toResponse(saved);
    }

    /** Books show their categories' names, so every cached book may hold the old name. */
    @Auditable(action = AuditAction.CATEGORY_UPDATE, target = "#id")
    @Caching(
            evict = {
                @CacheEvict(cacheNames = CacheNames.CATEGORIES, key = CacheNames.ALL),
                @CacheEvict(cacheNames = CacheNames.BOOKS, allEntries = true)
            })
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

    @Auditable(action = AuditAction.CATEGORY_DELETE, target = "#id")
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, key = CacheNames.ALL)
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
