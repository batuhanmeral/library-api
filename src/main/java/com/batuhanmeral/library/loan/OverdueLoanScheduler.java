package com.batuhanmeral.library.loan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the overdue check every night. Both values come from application.yml: the cron expression
 * (02:00 by default) and the zone it is read in, the same library.time-zone the application clock
 * uses. Without {@code zone} the cron would follow the server's time zone. The method runs on the
 * scheduler's thread (spring.task.scheduling); while it runs, the next trigger of the same method
 * waits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OverdueLoanScheduler {

    private final OverdueLoanJob job;

    @Scheduled(cron = "${library.loan.overdue-check.cron}", zone = "${library.time-zone}")
    void markOverdueLoans() {
        if (!job.tryClaim()) {
            log.info("Scheduled overdue check skipped: a run started by hand is still going");
            return;
        }
        job.runClaimed();
    }
}
