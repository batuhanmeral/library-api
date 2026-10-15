package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;

/**
 * İsteği gönderen kullanıcı, token'dan okunduğu haliyle. Controller'lara
 * {@code @AuthenticationPrincipal} ile gelir; token sadece id ve rol taşır.
 */
public record AuthenticatedUser(Long id, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** createdBy sütunlarında ve audit log'da görünen ad, örn. "user:12". */
    public String auditName() {
        return "user:" + id;
    }
}
