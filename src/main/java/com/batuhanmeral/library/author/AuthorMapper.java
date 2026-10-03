package com.batuhanmeral.library.author;

import com.batuhanmeral.library.author.dto.AuthorRequest;
import com.batuhanmeral.library.author.dto.AuthorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthorMapper {

    AuthorResponse toResponse(Author author);

    // Author.books has a getter but no setter; MapStruct would add to the list through the getter.
    @Mapping(target = "books", ignore = true)
    Author toEntity(AuthorRequest request);

    @Mapping(target = "books", ignore = true)
    void updateEntity(AuthorRequest request, @MappingTarget Author author);
}
