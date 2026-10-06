package com.batuhanmeral.library.loan.dto;

import com.batuhanmeral.library.loan.LoanStatus;
import jakarta.validation.constraints.Positive;

/** Optional filters of GET /loans; an unknown status value is a 400. */
public record LoanSearchCriteria(LoanStatus status, @Positive Long memberId) {}
