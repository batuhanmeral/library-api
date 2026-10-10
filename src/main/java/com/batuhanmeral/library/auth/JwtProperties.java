package com.batuhanmeral.library.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the access tokens, bound from {@code library.security.jwt.*}.
 *
 * @param secret the HMAC key, Base64 of at least 32 random bytes ({@code openssl rand -base64 32}).
 *     Whoever knows it can sign tokens for any user, so it comes from the environment (JWT_SECRET)
 *     and is never written into the repository.
 * @param expiration how long a token is accepted after it was issued
 * @param issuer written into every token ("iss") and required when one is read back
 */
@ConfigurationProperties("library.security.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret, @NotNull Duration expiration, @NotBlank String issuer) {

    /** Keeps the secret out of logs and error messages that print the properties. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, expiration=%s, issuer=%s]".formatted(expiration, issuer);
    }
}
