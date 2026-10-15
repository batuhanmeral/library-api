package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Micrometer'ın bellek içi registry'si ile ödünç sayaçları. */
class LoanMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private final LoanMetrics metrics = new LoanMetrics(registry);

    @Test
    void theCountersExistBeforeTheFirstLoan() {
        // Başlangıçta kaydedilir, dashboard "veri yok" yerine 0 gösterir.
        assertThat(registry.get("library.loans.borrowed").counter().count()).isZero();
        assertThat(registry.get("library.loans.returned").counter().count()).isZero();
    }

    @Test
    void eachCommittedLoanAndReturnIsCountedOnce() {
        LocalDate day = LocalDate.of(2026, 11, 25);
        metrics.onBorrowed(new LoanBorrowedEvent(1L, "ada@example.com", "Dune", day));
        metrics.onBorrowed(new LoanBorrowedEvent(2L, "ada@example.com", "Emma", day));
        metrics.onReturned(new LoanReturnedEvent(1L, "ada@example.com", "Dune", day));

        assertThat(registry.get("library.loans.borrowed").counter().count()).isEqualTo(2);
        assertThat(registry.get("library.loans.returned").counter().count()).isEqualTo(1);
    }
}
