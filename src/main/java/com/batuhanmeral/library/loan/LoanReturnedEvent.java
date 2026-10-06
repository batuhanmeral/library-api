package com.batuhanmeral.library.loan;

import java.time.LocalDate;

/** Published by LoanService when a loan is returned, inside the transaction. */
public record LoanReturnedEvent(
        Long loanId, String memberEmail, String bookTitle, LocalDate returnDate) {}
