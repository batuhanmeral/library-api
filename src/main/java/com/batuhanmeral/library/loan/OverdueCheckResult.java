package com.batuhanmeral.library.loan;

/** What one run of the nightly overdue check did. */
public record OverdueCheckResult(int loansMarked, int remindersSent) {}
