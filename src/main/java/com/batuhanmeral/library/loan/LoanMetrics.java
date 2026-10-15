package com.batuhanmeral.library.loan;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ödünç ve iadeleri /actuator/metrics için sayar (library.loans.borrowed, library.loans.returned).
 * Olayları commit'ten sonra dinler, tekrarlanan ödünç bir kez sayılır.
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
