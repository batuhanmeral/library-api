package com.batuhanmeral.library.loan.dto;

import com.batuhanmeral.library.loan.LoanStatus;

/** Optional filter of GET /loans/me. The member is the token's user, so there is no memberId. */
public record OwnLoanFilter(LoanStatus status) {}
