package com.batuhanmeral.library.loan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Loan rules that may differ between libraries, bound from {@code library.loan.*} in
 * application.yml. A record is bound through its constructor, so the values cannot change after
 * startup. A missing or invalid value stops the application at startup ({@code @Validated}).
 *
 * @param maxActiveLoans how many active or overdue loans a member may have at once
 * @param periodDays days from the loan date to the due date
 * @param overdueCheck the nightly job that marks overdue loans
 */
@ConfigurationProperties("library.loan")
@Validated
public record LoanProperties(
        @Min(1) int maxActiveLoans,
        @Min(1) @Max(365) int periodDays,
        @NotNull @Valid OverdueCheck overdueCheck) {

    /**
     * @param cron when the job runs, a Spring cron expression (second, minute, hour, day of month,
     *     month, day of week) read in library.time-zone
     * @param batchSize how many members one transaction of the job handles
     */
    public record OverdueCheck(@NotBlank String cron, @Min(1) @Max(1000) int batchSize) {}
}
