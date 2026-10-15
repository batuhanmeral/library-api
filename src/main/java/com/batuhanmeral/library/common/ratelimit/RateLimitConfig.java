package com.batuhanmeral.library.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Giriş ve kayıt denemelerini sınırlar (library.security.rate-limit); yoksa istemci BCrypt ile
 * CPU'yu meşgul ederek şifre deneyebilirdi. Testlerde kapalı, RateLimitTest kendi Redis'iyle açar.
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

    /** Request log interceptor'ından sonra: reddedilen istek de 429 ile log'lansın. */
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
