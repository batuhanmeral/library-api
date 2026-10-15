package com.batuhanmeral.library.loan;

/** Gece işinin bir çalışmasının sonucu. */
public record OverdueCheckResult(int loansMarked, int remindersSent) {}
