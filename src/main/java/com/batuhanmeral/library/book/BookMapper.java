package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * The request carries only ids for the author and the categories, and the copy counts follow rules
 * of their own; the service sets those fields, so the mapper leaves them alone.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BookMapper {

    // author -> AuthorSummary and each Category -> CategorySummary are generated as well.
    BookResponse toResponse(Book book);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "totalCopies", ignore = true)
    @Mapping(target = "availableCopies", ignore = true)
    Book toEntity(BookRequest request);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "totalCopies", ignore = true)
    @Mapping(target = "availableCopies", ignore = true)
    void updateEntity(BookRequest request, @MappingTarget Book book);
}
