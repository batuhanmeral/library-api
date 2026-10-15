package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;

/**
 * Testler için Authorization değerleri; uygulamanın kendi JwtService'i imzalar, istekler gerçek
 * filter'dan geçer.
 */
public final class TestTokens {

    /**
     * Admin token'larındaki kullanıcı id'si. Token veritabanına bakılmadan doğrulanır, admin'in
     * satırı gerekmez.
     */
    public static final long ADMIN_ID = 1_000_000;

    private TestTokens() {}

    public static String admin(JwtService jwt) {
        return bearer(jwt, ADMIN_ID, Role.ADMIN);
    }

    public static String member(JwtService jwt, long memberId) {
        return bearer(jwt, memberId, Role.MEMBER);
    }

    public static String bearer(JwtService jwt, long userId, Role role) {
        return "Bearer " + jwt.issue(new AuthenticatedUser(userId, role)).token();
    }
}
