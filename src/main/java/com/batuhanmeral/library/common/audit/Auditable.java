package com.batuhanmeral.library.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Writes an audit line for each call of the annotated method: what was done, to what, by whom and
 * whether it succeeded. AuditAspect does the writing; the annotation is only a marker it looks for.
 *
 * <p>Like {@code @Transactional}, it works through a proxy: only calls that come from another bean
 * are audited.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    AuditAction action();

    /**
     * A SpEL expression for the id of the resource, evaluated after the call. Parameters are
     * available by name ({@code #id}), the return value as {@code #result}; {@code #result?.id()}
     * gives null instead of an error when there is no result (the call failed). Empty: no target.
     */
    String target() default "";
}
