package com.batuhanmeral.library.loan.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /loans. The borrower is the user of the access token, not a field of the request: a
 * member cannot borrow in someone else's name. A "memberId" sent anyway is ignored.
 */
public record LoanRequest(@NotNull Long bookId) {}
