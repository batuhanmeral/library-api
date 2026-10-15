package com.batuhanmeral.library.loan.dto;

import com.batuhanmeral.library.loan.LoanStatus;
import jakarta.validation.constraints.Positive;

/** GET /loans filtreleri; bilinmeyen durum değeri 400. */
public record LoanSearchCriteria(LoanStatus status, @Positive Long memberId) {}
