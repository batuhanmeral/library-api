package com.batuhanmeral.library.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Limits how often one client may try to log in or register (library.security.rate-limit). Without
 * a limit, a client could try passwords as fast as the server checks them (about 100 ms each with
 * BCrypt, on every thread at once) and keep the CPU busy for everybody else.
 *
 * <p>Off when library.security.rate-limit.enabled is false: the tests log in far more often than a
 * person would, and RateLimitTest turns it on with its own Redis.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("library.security.rate-limit.enabled")
class RateLimitConfig implements WebMvcConfigurer {

    private static final String API = "/api/v1";

    private final RedisRateLimiter limiter;

    private final RateLimitProperties properties;

    RateLimitConfig(StringRedisTemplate redis, RateLimitProperties properties) {
        this.limiter = new RedisRateLimiter(redis);
        this.properties = properties;
    }

    /**
     * Ordered after the request log interceptor (WebMvcConfig, order 0), so a refused request is
     * still logged with its 429: only interceptors whose preHandle has run get afterCompletion.
     * Only POST is mapped on these paths; any other method gets 405 before an interceptor runs.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limiter, "login", properties.login()))
                .addPathPatterns(API + "/auth/login")
                .order(1);
        registry.addInterceptor(
                        new RateLimitInterceptor(limiter, "register", properties.register()))
                .addPathPatterns(API + "/auth/register")
                .order(1);
    }
}
