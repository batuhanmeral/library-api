package com.batuhanmeral.library.loan.dto;

import com.batuhanmeral.library.loan.LoanStatus;

/** GET /loans/me filtresi; üye token'dan gelir. */
public record OwnLoanFilter(LoanStatus status) {}
