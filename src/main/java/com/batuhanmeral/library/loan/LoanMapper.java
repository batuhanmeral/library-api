package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.loan.dto.LoanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LoanMapper {

    // BookSummary ve MemberSummary dönüşümleri de üretilir.
    LoanResponse toResponse(Loan loan);
}
