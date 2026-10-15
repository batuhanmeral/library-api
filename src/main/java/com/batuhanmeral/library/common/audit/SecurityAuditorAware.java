package com.batuhanmeral.library.common.audit;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Değişikliği kimin yaptığını söyler: token'daki kullanıcı ("user:12") ya da yoksa "system".
 * SecurityContext thread'e bağlıdır, {@code @Async} işler "system" görünür.
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
