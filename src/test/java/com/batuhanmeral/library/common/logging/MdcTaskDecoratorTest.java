package com.batuhanmeral.library.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * The MDC lives in a ThreadLocal: a task handed to another thread does not see the values of the
 * thread that submitted it, unless the decorator copies them over.
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() throws InterruptedException {
        MDC.clear();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void withoutTheDecoratorAnotherThreadDoesNotSeeTheMdc() throws Exception {
        MDC.put("requestId", "req-1");
        AtomicReference<String> seen = new AtomicReference<>("not run");

        executor.submit(() -> seen.set(MDC.get("requestId"))).get(5, TimeUnit.SECONDS);

        assertThat(seen.get()).isNull();
    }

    @Test
    void theTaskSeesTheMdcOfTheThreadThatSubmittedIt() throws Exception {
        MDC.put("requestId", "req-1");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> seen.set(MDC.get("requestId")));
        // Changes after submitting do not reach the task: it got a copy.
        MDC.put("requestId", "req-2");

        executor.submit(task).get(5, TimeUnit.SECONDS);

        assertThat(seen).hasValue("req-1");
    }

    @Test
    void thePoolThreadIsLeftAsItWasAfterTheTask() throws Exception {
        MDC.put("requestId", "req-1");
        executor.submit(decorator.decorate(() -> MDC.put("extra", "x"))).get(5, TimeUnit.SECONDS);
        AtomicReference<String> seen = new AtomicReference<>("not run");
        AtomicReference<String> extra = new AtomicReference<>("not run");

        MDC.clear();
        executor.submit(
                        () -> {
                            seen.set(MDC.get("requestId"));
                            extra.set(MDC.get("extra"));
                        })
                .get(5, TimeUnit.SECONDS);

        assertThat(seen.get()).isNull();
        assertThat(extra.get()).isNull();
    }

    @Test
    void aTaskSubmittedWithAnEmptyMdcRunsWithAnEmptyMdc() throws Exception {
        executor.submit(() -> MDC.put("requestId", "left over")).get(5, TimeUnit.SECONDS);
        AtomicReference<String> seen = new AtomicReference<>("not run");

        executor.submit(decorator.decorate(() -> seen.set(MDC.get("requestId"))))
                .get(5, TimeUnit.SECONDS);

        assertThat(seen.get()).isNull();
    }
}
