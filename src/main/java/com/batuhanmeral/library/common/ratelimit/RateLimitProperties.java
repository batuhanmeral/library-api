package com.batuhanmeral.library.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bir istemcinin gönderebileceği giriş ve kayıt isteği sayısı ({@code
 * library.security.rate-limit.*}).
 *
 * @param enabled false sınırları kapatır (testler RateLimitTest dışında kapatır)
 * @param login POST /auth/login: doğru ve yanlış her deneme sayılır
 * @param register POST /auth/register
 */
@ConfigurationProperties("library.security.rate-limit")
@Validated
public record RateLimitProperties(
        boolean enabled, @NotNull @Valid Rule login, @NotNull @Valid Rule register) {

    /**
     * {@code window} içinde en fazla {@code limit} istek. Pencere ilk istekle başlar, kaymaz. En az
     * bir saniye: Redis daha kısa süreli sayacı hemen siler.
     */
    public record Rule(@Positive int limit, @NotNull @DurationMin(seconds = 1) Duration window) {}
}
