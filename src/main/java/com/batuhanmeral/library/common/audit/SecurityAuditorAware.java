package com.batuhanmeral.library.common.audit;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tells JPA auditing (createdBy, updatedBy) and the audit log who makes a change: the user of the
 * request's token ("user:12"), or "system" when there is none, as for the nightly job or a
 * registration.
 *
 * <p>The SecurityContext belongs to the request's thread. Work handed to another thread
 * ({@code @Async}) does not see it and is recorded as "system".
 */
public class SecurityAuditorAware implements AuditorAware<String> {

    public static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.auditName());
        }
        return Optional.of(SYSTEM);
    }
}
