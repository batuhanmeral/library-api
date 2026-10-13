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
 * With scheduling turned on (it is off in the other tests), the nightly job is registered with the
 * cron expression and time zone from application.yml. The test does not wait for 02:00: it reads
 * the registration from ScheduledTaskHolder, which the bean post-processor behind
 * {@code @EnableScheduling} implements.
 */
@SpringBootTest(properties = "library.scheduling.enabled=true")
@ExtendWith(OutputCaptureExtension.class)
class OverdueLoanSchedulingTest implements DatabaseTest {

    @Autowired private ScheduledTaskHolder scheduledTasks;

    @Test
    void theOverdueCheckRunsEveryNightAtTwoInIstanbul() {
        CronTask cron = overdueCheck();
        assertThat(cron.getExpression()).isEqualTo("0 0 2 * * *");

        // Ask the trigger for the run after noon UTC on 24 November. 02:00 in Istanbul (UTC+3)
        // is 23:00 UTC; a trigger in the server's zone would answer 02:00 in that zone instead.
        Instant noonUtc = Instant.parse("2026-11-24T12:00:00Z");
        Instant next =
                cron.getTrigger()
                        .nextExecution(
                                new SimpleTriggerContext(Clock.fixed(noonUtc, ZoneOffset.UTC)));
        assertThat(next).isEqualTo(Instant.parse("2026-11-24T23:00:00Z"));
    }

    /**
     * No request started the nightly run, so there is no request id for its log lines. The run gets
     * an id of its own, the way a request does, and gives it back when it ends: the scheduler's
     * thread runs other tasks too.
     */
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
