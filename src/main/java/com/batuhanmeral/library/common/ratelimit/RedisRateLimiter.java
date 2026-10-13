package com.batuhanmeral.library.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Counts requests per client in Redis, so every copy of the application shares one count and a
 * restart does not reset it.
 *
 * <p>A fixed window: the first request of a client creates a counter that Redis deletes by itself
 * when the window ends (PEXPIRE); every request adds one (INCR). The script runs in Redis as one
 * step, so two requests at the same time cannot both see the counter missing, and a counter never
 * stays without its expiry (with INCR and PEXPIRE as two calls, a crash in between would block the
 * client for good).
 */
@Slf4j
public class RedisRateLimiter {

    /** The same prefix as the cache keys (spring.cache.redis.key-prefix). */
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
     * Counts one request of {@code client} against {@code rule}.
     *
     * @return empty if the request may go on, otherwise how long until the client may try again
     */
    public Optional<Duration> check(String name, String client, RateLimitProperties.Rule rule) {
        String key = KEY_PREFIX + name + ":" + client;
        List<?> result;
        try {
            result = redis.execute(COUNT, List.of(key), String.valueOf(rule.window().toMillis()));
        } catch (DataAccessException e) {
            // Without Redis the requests are not counted and all of them are let through:
            // refusing every login while Redis is down would lock everybody out. The log says
            // which exception, never the client.
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
