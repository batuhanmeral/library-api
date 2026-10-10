package com.batuhanmeral.library.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * A request sent a bearer token that cannot be accepted: malformed, signed with another key, from
 * another issuer, or expired. The message is the same for all of them; the client learns only that
 * it needs a new token, not which check failed.
 */
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException() {
        super("The access token is invalid or has expired.");
    }
}
