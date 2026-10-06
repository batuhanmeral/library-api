package com.batuhanmeral.library.loan;

import java.time.LocalDate;

/**
 * Published by LoanService when it lends a copy, inside the transaction. It carries plain values,
 * not the Loan entity: listeners that run after the commit have no persistence context, so lazy
 * relations of an entity could no longer be loaded.
 */
public record LoanBorrowedEvent(
        Long loanId, String memberEmail, String bookTitle, LocalDate dueDate) {}
