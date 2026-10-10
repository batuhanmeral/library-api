package com.batuhanmeral.library.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The administrator account created at startup, bound from {@code library.security.admin.*}.
 * Registration only creates members, so the first administrator has to come from somewhere else:
 * the environment (ADMIN_EMAIL, ADMIN_PASSWORD), never the code or a migration.
 *
 * @param email the account's address; when empty, no account is created
 * @param password at least 8 characters and at most 72 bytes; stored only as a BCrypt hash
 * @param fullName the name shown for the account, "Administrator" when not set
 */
@ConfigurationProperties("library.security.admin")
public record AdminAccountProperties(String email, String password, String fullName) {

    @Override
    public String toString() {
        return "AdminAccountProperties[email=%s, password=***, fullName=%s]"
                .formatted(email, fullName);
    }
}
