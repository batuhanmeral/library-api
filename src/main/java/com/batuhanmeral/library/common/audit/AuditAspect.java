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
 * Her {@link Auditable} çağrısı için "audit" logger'ına bir satır yazar:
 *
 * <pre>
 * action=BOOK_DELETE target=7 actor=user:1 outcome=SUCCESS
 * action=BOOK_DELETE target=7 actor=user:1 outcome=FAILURE reason=RESOURCE_IN_USE
 * </pre>
 *
 * <p>Hata durumunda sadece hata kodu yazılır, mesaj değil. Transaction proxy'sinin dışında çalışır,
 * SUCCESS commit'ten sonra yazılır. {@code @Retryable} hep en dışta olduğu için ödünç işlemleri
 * LoanController'da audit edilir.
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

    /** Her ifade bir kez parse edilir. */
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    public AuditAspect(AuditorAware<String> auditorAware) {
        this.auditorAware = auditorAware;
    }

    /**
     * {@code @annotation(auditable)} anotasyonu taşıyan metodları eşler ve onu aynı adlı
     * parametreye bağlar.
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
            // Yanlış ifade anotasyondaki bir hatadır (AuditTrailTest hepsini parse eder);
            // değişiklik zaten commit edildi, çağrı audit yüzünden düşmemeli.
            log.warn(
                    "Audit target '{}' of {} could not be evaluated: {}",
                    auditable.target(),
                    method.getName(),
                    e.getClass().getSimpleName());
            return UNKNOWN;
        }
    }

    /**
     * Sadece parametreler ve sonuç. SimpleEvaluationContext tip, constructor ve bean referansına
     * izin vermez.
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
