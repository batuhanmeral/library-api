package com.batuhanmeral.library.user;

import com.batuhanmeral.library.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Sadece entity'den cevaba. Şifre hedef alan değil, yanlışlıkla kopyalanamaz. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(User user);
}
