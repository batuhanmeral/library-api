package com.batuhanmeral.library.loan;

import java.time.LocalDate;

/** LoanService ödünç iade edildiğinde, transaction içinde yayınlar. */
public record LoanReturnedEvent(
        Long loanId, String memberEmail, String bookTitle, LocalDate returnDate) {}
