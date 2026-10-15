package com.batuhanmeral.library.loan;

import java.time.LocalDate;

/**
 * LoanService kopya verdiğinde, transaction içinde yayınlar. Entity değil düz değer taşır: commit
 * sonrası listener'ın persistence context'i yok.
 */
public record LoanBorrowedEvent(
        Long loanId, String memberEmail, String bookTitle, LocalDate dueDate) {}
