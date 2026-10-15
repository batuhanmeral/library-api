package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.common.logging.RequestIdFilter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gece işini her gece başlatır; cron ve saat dilimi application.yml'den gelir. Çalışmaya kendi
 * request id'si verilir ("overdue-check-1a2b3c4d").
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
            // Scheduler thread'i başka işler de çalıştırır.
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }
}
