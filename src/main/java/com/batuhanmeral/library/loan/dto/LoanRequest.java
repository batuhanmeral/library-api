package com.batuhanmeral.library.loan.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /loans. Until sign-in exists (step 10) the member is sent in the request; after that
 * it comes from the token and a member can no longer borrow in someone else's name.
 */
public record LoanRequest(@NotNull Long bookId, @NotNull Long memberId) {}
