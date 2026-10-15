package com.batuhanmeral.library.loan;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Hakkı alınmış gece işini task executor'da çalıştırır, HTTP isteği beklemez. {@code @Async} proxy
 * ile çalıştığı için ayrı bean.
 */
@Component
@RequiredArgsConstructor
class OverdueLoanJobRunner {

    private final OverdueLoanJob job;

    @Async
    public void runClaimed() {
        job.runClaimed();
    }
}
