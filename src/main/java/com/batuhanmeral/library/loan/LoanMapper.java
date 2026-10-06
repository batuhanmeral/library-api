package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.loan.dto.LoanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LoanMapper {

    // book -> BookSummary(id, title) and member -> MemberSummary(id, fullName) are generated.
    LoanResponse toResponse(Loan loan);
}
