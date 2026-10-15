package com.batuhanmeral.library.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * İşaretli metodun her çağrısı için audit satırı yazdırır (AuditAspect). {@code @Transactional}
 * gibi proxy ile çalışır: sadece başka bean'den gelen çağrılar audit edilir.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    AuditAction action();

    /**
     * Kaynak id'si için çağrıdan sonra değerlendirilen SpEL ifadesi: {@code #id}, {@code
     * #result?.id()}. Boş: hedef yok.
     */
    String target() default "";
}
