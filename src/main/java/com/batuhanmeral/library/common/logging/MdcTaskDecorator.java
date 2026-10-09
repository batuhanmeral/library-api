package com.batuhanmeral.library.common.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the MDC (the request id) over to the thread that runs an {@code @Async} task. The MDC is
 * kept per thread (ThreadLocal); without this, the log lines of a job started by a request would
 * have no request id. Spring Boot applies a TaskDecorator bean to the executor it configures (see
 * AsyncConfig).
 *
 * <p>The copy is taken when the task is submitted, in the request's thread. After the task, the
 * pool thread gets back the MDC it had before, so nothing leaks into its next task.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setContext(submitted);
            try {
                task.run();
            } finally {
                setContext(previous);
            }
        };
    }

    private static void setContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
