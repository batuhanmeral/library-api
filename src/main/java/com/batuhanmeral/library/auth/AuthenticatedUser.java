package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;

/**
 * The user who sent a request, as a valid access token names them. This is the principal that
 * JwtAuthenticationFilter puts into the SecurityContext; controllers receive it with
 * {@code @AuthenticationPrincipal}. It holds no email or name: the token carries only the id and
 * the role.
 */
public record AuthenticatedUser(Long id, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** How the user appears in the createdBy columns and the audit log, e.g. "user:12". */
    public String auditName() {
        return "user:" + id;
    }
}
