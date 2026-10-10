package com.batuhanmeral.library.config;

import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns on Spring's cache annotations and sets up the Redis caches.
 *
 * <p>Order: the cache advice runs just outside the transaction advice (LOWEST_PRECEDENCE). A cache
 * hit then returns before a transaction and a database connection are taken, and an entry is
 * dropped only after the change was committed; dropped earlier, a request in between could read the
 * old row and put it back. The timing and audit aspects (step 9) stay outside both.
 *
 * <p>Which store is used comes from spring.cache.type: Redis in the application, none in most
 * tests, an in-memory map in CachingTest.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    /**
     * The cache only makes reads faster, so a cache that fails (Redis down or slow) must not fail
     * the request: the error is logged, without the stack trace, and the method runs as if there
     * were no cache. Spring's default handler would rethrow, and every read would answer 500.
     *
     * <p>The line names the cache, the key and the exception type
     * ("RedisConnectionFailureException" or "SerializationException"), never the value or the
     * exception's message.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(CacheConfig.class.getName(), false) {
            @Override
            protected void logCacheError(Supplier<String> message, RuntimeException exception) {
                getLogger().warn(message.get() + " (" + exception.getClass().getSimpleName() + ")");
            }
        };
    }

    /**
     * Adjusts the RedisCacheManager that Spring Boot builds when spring.cache.type is redis.
     *
     * <ul>
     *   <li>Each cache stores one type as plain JSON. Spring Boot's default is Java serialization,
     *       which needs Serializable classes and produces bytes nobody can read. A JSON serializer
     *       that writes the class name into the value (default typing) could read any type, but
     *       then whoever can write to Redis chooses which class is created when it is read back.
     *   <li>Entries expire after the time to live of their cache.
     *   <li>No cache is created for a name not listed here.
     *   <li>allEntries evictions find the keys with SCAN, in batches, instead of KEYS, which blocks
     *       Redis until it has looked at every key.
     * </ul>
     */
    @Bean
    RedisCacheManagerBuilderCustomizer libraryCaches(
            JsonMapper jsonMapper,
            LibraryCacheProperties properties,
            RedisConnectionFactory connectionFactory) {
        JavaType book = jsonMapper.getTypeFactory().constructType(BookResponse.class);
        JavaType categories =
                jsonMapper
                        .getTypeFactory()
                        .constructCollectionType(List.class, CategoryResponse.class);
        return builder -> {
            RedisCacheConfiguration defaults = builder.cacheDefaults();
            builder.cacheWriter(
                            RedisCacheWriter.nonLockingRedisCacheWriter(
                                    connectionFactory, BatchStrategies.scan(1000)))
                    .withCacheConfiguration(
                            CacheNames.BOOKS,
                            json(defaults, jsonMapper, book).entryTtl(properties.booksTtl()))
                    .withCacheConfiguration(
                            CacheNames.CATEGORIES,
                            json(defaults, jsonMapper, categories)
                                    .entryTtl(properties.categoriesTtl()))
                    .disableCreateOnMissingCache();
        };
    }

    private static RedisCacheConfiguration json(
            RedisCacheConfiguration defaults, JsonMapper jsonMapper, JavaType type) {
        return defaults.serializeValuesWith(
                SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, type)));
    }
}
