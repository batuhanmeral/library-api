package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;

/**
 * Authorization header values for tests. The tokens are signed by the application's own JwtService,
 * so requests that carry them go through the real filter, as a client's would.
 */
public final class TestTokens {

    /**
     * The user id in admin tokens. Tokens are checked without reading the database, so an admin
     * needs no row to manage the catalogue; the id only shows up in createdBy and the audit log.
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
