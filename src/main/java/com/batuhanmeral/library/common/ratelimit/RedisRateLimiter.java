package com.batuhanmeral.library.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * İstekleri Redis'te sayar: uygulamanın her kopyası aynı sayacı paylaşır, yeniden başlatma
 * sıfırlamaz.
 *
 * <p>Sabit pencere: ilk istek sayacı süreyle oluşturur (PEXPIRE), her istek bir artırır (INCR).
 * Betik Redis'te tek adımda çalışır, sayaç süresiz kalmaz.
 */
@Slf4j
public class RedisRateLimiter {

    /** Cache anahtarlarıyla aynı önek. */
    static final String KEY_PREFIX = "library-api:rate-limit:";

    private static final RedisScript<List> COUNT =
            RedisScript.of(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                      redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return {count, redis.call('PTTL', KEYS[1])}
                    """,
                    List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * {@code client}'ın bir isteğini {@code rule}'a göre sayar.
     *
     * @return istek devam edebiliyorsa boş, yoksa tekrar denemeye kalan süre
     */
    public Optional<Duration> check(String name, String client, RateLimitProperties.Rule rule) {
        String key = KEY_PREFIX + name + ":" + client;
        List<?> result;
        try {
            result = redis.execute(COUNT, List.of(key), String.valueOf(rule.window().toMillis()));
        } catch (DataAccessException e) {
            // Redis yoksa istekler sayılmadan geçer: Redis kapalıyken her girişi reddetmek herkesi
            // dışarıda bırakırdı. Log istemciyi yazmaz.
            log.warn(
                    "Rate limit for {} not checked: Redis did not answer ({})",
                    name,
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
        long count = ((Number) result.get(0)).longValue();
        long millisLeft = ((Number) result.get(1)).longValue();
        if (count <= rule.limit()) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(Math.max(millisLeft, 0)));
    }
}
