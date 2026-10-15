package com.batuhanmeral.library.loan.dto;

import com.batuhanmeral.library.loan.LoanStatus;
import java.time.Instant;
import java.time.LocalDate;

/** API'nin döndüğü ödünç, kitap ve üye özetiyle. */
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

    /** E-posta ve telefon yok: ödünç listesi iletişim bilgisi göstermez. */
    public record MemberSummary(Long id, String fullName) {}
}
