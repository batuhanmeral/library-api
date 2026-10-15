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
 * Yazar ve kategorileri service bağlar. Kopya sayıları ve version'ın setter'ı yok, MapStruct onlara
 * dokunmaz.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BookMapper {

    // AuthorSummary ve CategorySummary dönüşümleri de üretilir.
    BookResponse toResponse(Book book);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    Book toEntity(BookRequest request);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    void updateEntity(BookRequest request, @MappingTarget Book book);

    /** PATCH: istekte null olan alan kitaptaki değere dokunmaz. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "categories", ignore = true)
    void patchEntity(BookPatchRequest request, @MappingTarget Book book);
}
