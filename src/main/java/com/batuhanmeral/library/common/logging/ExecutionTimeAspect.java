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
 * Her {@code @Service} public metodunun süresini ölçer. Her çağrı DEBUG'a, eşiği aşanlar WARN'a
 * yazılır; argüman ve sonuç log'lanmaz.
 *
 * <p>Audit aspect'inin ve transaction'ın önünde çalışır, süreye commit dahildir. {@code @Retryable}
 * istisnadır: orada her deneme ayrı ölçülür.
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

    /** Bu uygulamadaki {@code @Service} sınıflarının public metodları. */
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
            return; // Production'da çoğu çağrı: mesaj hiç kurulmaz.
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

    /** Proxy'nin değil, metodun tanımlandığı sınıfın adı. */
    private static String nameOf(ProceedingJoinPoint call) {
        return call.getSignature().getDeclaringType().getSimpleName()
                + "."
                + call.getSignature().getName();
    }
}
