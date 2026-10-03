package com.batuhanmeral.library.common.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;

/**
 * Tells JPA auditing who makes a change, for the createdBy and updatedBy columns. There are no
 * logged-in users yet, so every change is recorded as made by "system".
 */
public class SystemAuditorAware implements AuditorAware<String> {

    public static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SYSTEM);
    }
}
