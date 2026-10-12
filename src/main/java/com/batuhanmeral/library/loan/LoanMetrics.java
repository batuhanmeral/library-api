package com.batuhanmeral.library.loan;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Counts loans and returns for /actuator/metrics (library.loans.borrowed, library.loans.returned).
 * Micrometer's MeterRegistry is what Spring Boot's metrics are made of; a monitoring system
 * (Prometheus, Datadog, ...) reads the same counters once its registry is on the classpath.
 *
 * <p>Like the confirmation emails, the counters listen to the loan events after the commit: a
 * borrow that is rolled back or retried counts once, for the attempt that committed. The counters
 * are registered when the bean is created, so they show 0 before the first loan instead of being
 * missing.
 */
@Component
class LoanMetrics {

    private final Counter borrowed;
    private final Counter returned;

    LoanMetrics(MeterRegistry registry) {
        borrowed =
                Counter.builder("library.loans.borrowed")
                        .description("Loans committed since the application started")
                        .register(registry);
        returned =
                Counter.builder("library.loans.returned")
                        .description("Returns committed since the application started")
                        .register(registry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBorrowed(LoanBorrowedEvent event) {
        borrowed.increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onReturned(LoanReturnedEvent event) {
        returned.increment();
    }
}
