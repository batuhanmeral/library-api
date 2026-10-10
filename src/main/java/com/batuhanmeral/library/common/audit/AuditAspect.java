package com.batuhanmeral.library.common.audit;

import com.batuhanmeral.library.common.error.ApiException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.AuditorAware;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Writes one audit line for every call of an {@link Auditable} method:
 *
 * <pre>
 * action=BOOK_DELETE target=7 actor=user:1 outcome=SUCCESS
 * action=BOOK_DELETE target=7 actor=user:1 outcome=FAILURE reason=RESOURCE_IN_USE
 * </pre>
 *
 * The lines go to a logger of their own named "audit", so they can be sent to their own file or
 * kept longer than the rest of the log. The request id is on them like on every line (MDC).
 *
 * <p>The actor comes from the same AuditorAware as the createdBy columns: the user of the request's
 * token ("user:12"), or "system" without one. A failure names only the error code (or the exception
 * type), never the exception's message, which may repeat what the user sent.
 *
 * <p>Order: outside the transaction proxy. "SUCCESS" is written after the commit, so a change that
 * fails while being committed is audited as a failure. {@code @Retryable} cannot be wrapped this
 * way (its interceptor always comes first), so borrowing and returning are audited on
 * LoanController, outside LoanFacade's retries: a borrow that needed three attempts is one audited
 * action.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private static final String NONE = "-";

    private static final String UNKNOWN = "?";

    private final AuditorAware<String> auditorAware;

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    /** Parsed once per expression text; parsing on every call would waste time. */
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    public AuditAspect(AuditorAware<String> auditorAware) {
        this.auditorAware = auditorAware;
    }

    /**
     * {@code @annotation(auditable)} matches methods that carry the annotation and binds it to the
     * parameter of the same name, so the advice can read its attributes.
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint call, Auditable auditable) throws Throwable {
        Object result;
        try {
            result = call.proceed();
        } catch (Throwable e) {
            audit.info(
                    "action={} target={} actor={} outcome=FAILURE reason={}",
                    auditable.action(),
                    target(call, auditable, null),
                    actor(),
                    reasonOf(e));
            throw e;
        }
        audit.info(
                "action={} target={} actor={} outcome=SUCCESS",
                auditable.action(),
                target(call, auditable, result),
                actor());
        return result;
    }

    private String target(ProceedingJoinPoint call, Auditable auditable, Object result) {
        if (auditable.target().isEmpty()) {
            return NONE;
        }
        Method method = ((MethodSignature) call.getSignature()).getMethod();
        try {
            Object value =
                    expressions
                            .computeIfAbsent(auditable.target(), parser::parseExpression)
                            .getValue(contextFor(method, call.getArgs(), result));
            return value == null ? NONE : value.toString();
        } catch (RuntimeException e) {
            // A wrong expression is a bug in the annotation (AuditTrailTest parses them all), but
            // the change is already committed here: the call must not fail because of the audit.
            log.warn(
                    "Audit target '{}' of {} could not be evaluated: {}",
                    auditable.target(),
                    method.getName(),
                    e.getClass().getSimpleName());
            return UNKNOWN;
        }
    }

    /**
     * Parameters by name and the result, nothing else. SimpleEvaluationContext allows reading them
     * and calling their methods ({@code #result?.id()}), but no types ({@code T(...)}), no
     * constructors and no bean references, unlike the full StandardEvaluationContext.
     */
    private EvaluationContext contextFor(Method method, Object[] args, Object result) {
        SimpleEvaluationContext context =
                SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
        String[] names = parameterNames.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        context.setVariable("result", result);
        return context;
    }

    private String actor() {
        try {
            return auditorAware.getCurrentAuditor().orElse(UNKNOWN);
        } catch (RuntimeException e) {
            log.warn("Audit actor could not be read: {}", e.getClass().getSimpleName());
            return UNKNOWN;
        }
    }

    private static String reasonOf(Throwable e) {
        return e instanceof ApiException api
                ? api.getErrorCode().name()
                : e.getClass().getSimpleName();
    }
}
