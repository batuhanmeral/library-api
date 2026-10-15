package com.batuhanmeral.library.config;

import com.batuhanmeral.library.common.audit.SecurityAuditorAware;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing'i açar. Ayrı sınıfta, JPA'sı olmayan {@code @WebMvcTest} gibi testler yüklemesin
 * diye.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return new SecurityAuditorAware();
    }

    /**
     * Instant.now() nanosaniye, sütunlar mikrosaniye tutar; kırpılmazsa cevaptaki değer
     * veritabanındakinden farklı olur.
     */
    @Bean
    DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
