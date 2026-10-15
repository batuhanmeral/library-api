package com.batuhanmeral.library.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Kayıtlı yollara gelen istekleri sayar ve sınırı aşanları controller'dan önce reddeder; reddedilen
 * giriş şifre kontrol etmez.
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
