package com.batuhanmeral.library.user;

import com.batuhanmeral.library.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Entity to response only. ERROR on unmapped targets; the password is not a target field, so it
 * cannot be copied by mistake.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(User user);
}
