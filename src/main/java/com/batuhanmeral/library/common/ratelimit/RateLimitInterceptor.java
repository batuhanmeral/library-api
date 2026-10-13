package com.batuhanmeral.library.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Counts each request to the paths it is registered for (RateLimitConfig) and refuses the ones over
 * the limit before the controller runs: a refused login checks no password, so it costs no BCrypt
 * work and tells nothing about the password.
 *
 * <p>The exception goes to GlobalExceptionHandler like one from a controller: DispatcherServlet
 * hands exceptions from preHandle to the same exception resolvers.
 */
class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter limiter;

    private final String name;

    private final RateLimitProperties.Rule rule;

    RateLimitInterceptor(RedisRateLimiter limiter, String name, RateLimitProperties.Rule rule) {
        this.limiter = limiter;
        this.name = name;
        this.rule = rule;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        limiter.check(name, ClientAddress.keyOf(request.getRemoteAddr()), rule)
                .ifPresent(retryAfter -> {
                    throw new TooManyRequestsException(retryAfter);
                });
        return true;
    }
}
