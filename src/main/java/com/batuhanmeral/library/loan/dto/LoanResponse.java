package com.batuhanmeral.library.loan.dto;

import com.batuhanmeral.library.loan.LoanStatus;
import java.time.Instant;
import java.time.LocalDate;

/** A loan as the API returns it, with a short summary of the book and the member. */
public record LoanResponse(
        Long id,
        BookSummary book,
        MemberSummary member,
        LocalDate loanDate,
        LocalDate dueDate,
        LocalDate returnDate,
        LoanStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public record BookSummary(Long id, String title) {}

    /** No email or phone: a loan list should not expose members' contact details. */
    public record MemberSummary(Long id, String fullName) {}
}
