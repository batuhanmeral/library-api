package com.batuhanmeral.library.category;

import com.batuhanmeral.library.category.dto.CategoryRequest;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

// unmappedTargetPolicy = ERROR: eşlenmeyen hedef alan derlemeyi düşürür.
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    Category toEntity(CategoryRequest request);

    void updateEntity(CategoryRequest request, @MappingTarget Category category);
}
