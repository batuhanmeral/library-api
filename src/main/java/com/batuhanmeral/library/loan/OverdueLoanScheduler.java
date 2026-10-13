package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.common.logging.RequestIdFilter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the overdue check every night. Both values come from application.yml: the cron expression
 * (02:00 by default) and the zone it is read in, the same library.time-zone the application clock
 * uses. Without {@code zone} the cron would follow the server's time zone. The method runs on the
 * scheduler's thread (spring.task.scheduling); while it runs, the next trigger of the same method
 * waits.
 *
 * <p>No request started the run, so it has no request id; it gets one of its own
 * ("overdue-check-1a2b3c4d") in the same MDC key, and every log line of the run carries it. A run
 * started by hand (POST /admin/jobs/overdue-check) keeps the id of its request instead
 * (MdcTaskDecorator).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OverdueLoanScheduler {

    private final OverdueLoanJob job;

    @Scheduled(cron = "${library.loan.overdue-check.cron}", zone = "${library.time-zone}")
    void markOverdueLoans() {
        MDC.put(
                RequestIdFilter.MDC_KEY,
                "overdue-check-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            if (!job.tryClaim()) {
                log.info("Scheduled overdue check skipped: a run started by hand is still going");
                return;
            }
            job.runClaimed();
        } finally {
            // The scheduler's thread runs other tasks too.
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }
}
