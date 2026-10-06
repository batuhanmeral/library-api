package com.batuhanmeral.library.loan;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs a claimed overdue check on the application's task executor, so the HTTP request that started
 * it does not wait. {@code @Async} works through a proxy, like {@code @Transactional}: the call
 * must come from another bean, which is why this is not a method of OverdueLoanJob or the
 * controller. An exception thrown here is only logged (there is no caller left to receive it).
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
