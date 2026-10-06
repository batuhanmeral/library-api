package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookPatchRequest;
import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * The request carries only ids for the author and the categories; the service sets those. The copy
 * counts and the version have no setters (copies follow rules of their own, see Book), so MapStruct
 * does not see them as targets at all.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BookMapper {

    // author -> AuthorSummary and each Category -> CategorySummary are generated as well.
    BookResponse toResponse(Book book);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    Book toEntity(BookRequest request);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    void updateEntity(BookRequest request, @MappingTarget Book book);

    /**
     * PATCH: a null field in the request leaves the book's value alone. Without IGNORE, MapStruct
     * would call {@code book.setIsbn(null)} for every field that was not sent.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    void patchEntity(BookPatchRequest request, @MappingTarget Book book);
}
