package com.batuhanmeral.library.category;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    public Category create(Category request) {
        // Copy only the fields a client may set; an id in the request body is ignored.
        Category category = new Category(request.getName(), request.getDescription());
        return categoryRepository.save(category);
    }

    public Optional<Category> update(Long id, Category request) {
        return categoryRepository
                .findById(id)
                .map(category -> {
                    category.setName(request.getName());
                    category.setDescription(request.getDescription());
                    return categoryRepository.save(category);
                });
    }

    public boolean delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            return false;
        }
        categoryRepository.deleteById(id);
        return true;
    }
}
