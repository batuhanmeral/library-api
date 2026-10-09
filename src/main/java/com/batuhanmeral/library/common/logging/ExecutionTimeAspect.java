package com.batuhanmeral.library.common.logging;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Measures how long each public method of a {@code @Service} class takes, without a line of timing
 * code in the services themselves: a cross-cutting concern, kept in one place.
 *
 * <p>Every call is logged at DEBUG ({@code BookService.findById took 3 ms}); a call that takes at
 * least library.logging.slow-call-threshold is a WARN ({@code Slow call: ...}). Arguments and
 * results are not logged: they may hold personal data, and the request log already says what was
 * asked.
 *
 * <p>Order: in front of the audit aspect and the transaction, so the time includes the commit. The
 * one exception is {@code @Retryable} (LoanFacade): Spring always puts the retry interceptor in
 * front of a bean's other advice, so there each attempt is timed on its own.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExecutionTimeAspect {

    private final long slowCallNanos;

    public ExecutionTimeAspect(LoggingProperties properties) {
        this.slowCallNanos = properties.slowCallThreshold().toNanos();
    }

    /**
     * The pointcut: public methods ({@code execution(public * *(..))}) of classes annotated with
     * {@code @Service} ({@code @within}) in this application ({@code within}).
     */
    @Around(
            "execution(public * *(..))"
                    + " && @within(org.springframework.stereotype.Service)"
                    + " && within(com.batuhanmeral.library..*)")
    public Object time(ProceedingJoinPoint call) throws Throwable {
        long start = System.nanoTime();
        Throwable failure = null;
        try {
            return call.proceed();
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            log(call, System.nanoTime() - start, failure);
        }
    }

    private void log(ProceedingJoinPoint call, long nanos, Throwable failure) {
        boolean slow = nanos >= slowCallNanos;
        if (!slow && !log.isDebugEnabled()) {
            return; // Most calls, in production: do not even build the message.
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        String what =
                failure == null
                        ? nameOf(call) + " took " + millis + " ms"
                        : nameOf(call)
                                + " failed after "
                                + millis
                                + " ms ("
                                + failure.getClass().getSimpleName()
                                + ")";
        if (slow) {
            log.warn("Slow call: {}", what);
        } else {
            log.debug("{}", what);
        }
    }

    /** {@code BookService.findById}: the class the method is declared in, not the proxy's. */
    private static String nameOf(ProceedingJoinPoint call) {
        return call.getSignature().getDeclaringType().getSimpleName()
                + "."
                + call.getSignature().getName();
    }
}
