package com.batuhanmeral.library.loan.dto;

import jakarta.validation.constraints.NotNull;

/** POST /loans gövdesi. Ödünç alan token'daki kullanıcıdır; gelen "memberId" yok sayılır. */
public record LoanRequest(@NotNull Long bookId) {}
