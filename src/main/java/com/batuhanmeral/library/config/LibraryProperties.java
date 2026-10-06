package com.batuhanmeral.library.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the whole library, bound from {@code library.*}. The loan rules have their own record
 * (LoanProperties); keys this record does not know are left to it.
 *
 * @param timeZone the library's time zone: "today" for loans and the time of the nightly job. Dates
 *     such as a loan's due date are local dates in this zone; timestamps stay in UTC.
 * @param scheduling the scheduled jobs
 */
@ConfigurationProperties("library")
@Validated
public record LibraryProperties(@NotNull ZoneId timeZone, @NotNull @Valid Scheduling scheduling) {

    /**
     * @param enabled whether scheduled jobs run by themselves (see SchedulingConfig); off in tests
     */
    public record Scheduling(boolean enabled) {}
}
