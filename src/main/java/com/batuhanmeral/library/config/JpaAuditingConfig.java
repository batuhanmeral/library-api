package com.batuhanmeral.library.config;

import com.batuhanmeral.library.common.audit.SystemAuditorAware;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on JPA auditing. It lives in its own class instead of on LibraryApplication so that slice
 * tests such as {@code @WebMvcTest}, which have no JPA, do not load it.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return new SystemAuditorAware();
    }

    /**
     * Instant.now() has nanoseconds, the timestamp columns keep microseconds. Without truncating,
     * the value in a create response differs from the one read back from the database later.
     */
    @Bean
    DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
