package com.batuhanmeral.library.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * Kabul edilemeyen bearer token (bozuk, başka anahtar, başka issuer, süresi dolmuş). Mesaj hepsinde
 * aynı: istemci hangi kontrolün düştüğünü öğrenmez.
 */
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException() {
        super("The access token is invalid or has expired.");
    }
}
