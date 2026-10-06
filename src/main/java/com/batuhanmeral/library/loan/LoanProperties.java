package com.batuhanmeral.library.loan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Loan rules that may differ between libraries, bound from {@code library.loan.*} in
 * application.yml. A record is bound through its constructor, so the values cannot change after
 * startup. A missing or invalid value stops the application at startup ({@code @Validated}).
 *
 * @param maxActiveLoans how many active or overdue loans a member may have at once
 * @param periodDays days from the loan date to the due date
 */
@ConfigurationProperties("library.loan")
@Validated
public record LoanProperties(@Min(1) int maxActiveLoans, @Min(1) @Max(365) int periodDays) {}
