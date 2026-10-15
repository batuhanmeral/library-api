package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * Zamanlama açıkken gece işi application.yml'deki cron ve saat dilimiyle kaydedilir. Test 02:00'yi
 * beklemez, kaydı ScheduledTaskHolder'dan okur.
 */
@SpringBootTest(properties = "library.scheduling.enabled=true")
@ExtendWith(OutputCaptureExtension.class)
class OverdueLoanSchedulingTest implements DatabaseTest {

    @Autowired private ScheduledTaskHolder scheduledTasks;

    @Test
    void theOverdueCheckRunsEveryNightAtTwoInIstanbul() {
        CronTask cron = overdueCheck();
        assertThat(cron.getExpression()).isEqualTo("0 0 2 * * *");

        // 24 Kasım öğlen UTC'den sonraki çalışma: İstanbul'da 02:00 (UTC+3), UTC'de 23:00 eder.
        Instant noonUtc = Instant.parse("2026-11-24T12:00:00Z");
        Instant next =
                cron.getTrigger()
                        .nextExecution(
                                new SimpleTriggerContext(Clock.fixed(noonUtc, ZoneOffset.UTC)));
        assertThat(next).isEqualTo(Instant.parse("2026-11-24T23:00:00Z"));
    }

    /** Gece çalışmasını istek başlatmadı; kendi id'sini alır ve bitince geri verir. */
    @Test
    void aScheduledRunHasAnIdInItsLogLines(CapturedOutput output) {
        overdueCheck().getRunnable().run();

        assertThat(output)
                .containsPattern("\\[overdue-check-[0-9a-f]{8}] .*Overdue check for \\d{4}-");
        assertThat(MDC.get("requestId")).isNull();
    }

    private CronTask overdueCheck() {
        ScheduledTask task =
                scheduledTasks.getScheduledTasks().stream()
                        .filter(t -> t.toString().contains("OverdueLoanScheduler.markOverdueLoans"))
                        .findFirst()
                        .orElseThrow();
        assertThat(task.getTask()).isInstanceOf(CronTask.class);
        return (CronTask) task.getTask();
    }
}
