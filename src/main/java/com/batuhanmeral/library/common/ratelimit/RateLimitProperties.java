package com.batuhanmeral.library.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How many sign-in and registration requests one client may send, bound from {@code
 * library.security.rate-limit.*}.
 *
 * @param enabled false turns the limits off (the tests do, except RateLimitTest)
 * @param login POST /auth/login: every attempt counts, the right and the wrong ones
 * @param register POST /auth/register
 */
@ConfigurationProperties("library.security.rate-limit")
@Validated
public record RateLimitProperties(
        boolean enabled, @NotNull @Valid Rule login, @NotNull @Valid Rule register) {

    /**
     * At most {@code limit} requests in a window of {@code window}. The window starts with the
     * first request and does not slide: after it ends, the client has the full limit again.
     */
    public record Rule(@Positive int limit, @NotNull Duration window) {}
}
