package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.category.dto.CategoryResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Redis side of the cache, without a Redis server: building caches and turning values into
 * bytes and back needs no connection. Reading from and writing to a real Redis is checked by hand
 * (and in step 12 with Testcontainers).
 */
@ExtendWith(OutputCaptureExtension.class)
class CacheConfigTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:15:30.123456Z");

    private final RedisCacheManager cacheManager = cacheManager();

    @Test
    void theTwoCachesHaveTheirOwnTimeToLive() {
        assertThat(ttlOf(CacheNames.BOOKS)).isEqualTo(Duration.ofMinutes(10));
        assertThat(ttlOf(CacheNames.CATEGORIES)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void aCacheNameWithATypoIsNotCreatedSilently() {
        // Otherwise it would get the default settings: no TTL and Java serialization.
        assertThat(cacheManager.getCache("book")).isNull();
    }

    @Test
    void aBookIsStoredAsJsonAndReadBackAsTheSameRecord() {
        BookResponse book =
                new BookResponse(
                        7L,
                        "9780441172719",
                        "Dune",
                        null,
                        1965,
                        412,
                        2,
                        1,
                        new BookResponse.AuthorSummary(3L, "Frank Herbert"),
                        List.of(new BookResponse.CategorySummary(4L, "Science fiction")),
                        NOW,
                        NOW);

        ByteBuffer stored = configOf(CacheNames.BOOKS).getValueSerializationPair().write(book);

        // Plain JSON, readable in redis-cli, with no Java class names in it.
        assertThat(StandardCharsets.UTF_8.decode(stored.duplicate()).toString())
                .contains("\"title\":\"Dune\"", "\"createdAt\":\"2026-11-25T10:15:30.123456Z\"")
                .doesNotContain("com.batuhanmeral", "@class");
        assertThat(configOf(CacheNames.BOOKS).getValueSerializationPair().read(stored))
                .isEqualTo(book);
    }

    @Test
    void theCategoryListIsReadBackAsAListOfRecords() {
        List<CategoryResponse> categories =
                List.of(new CategoryResponse(1L, "Science", null, NOW, NOW));

        ByteBuffer stored =
                configOf(CacheNames.CATEGORIES).getValueSerializationPair().write(categories);

        assertThat(configOf(CacheNames.CATEGORIES).getValueSerializationPair().read(stored))
                .isEqualTo(categories);
    }

    @Test
    void aFailingCacheIsLoggedNotThrown() {
        // The cache only makes reads faster. When Redis is down, the request goes to the
        // database instead of failing.
        Cache cache = new ConcurrentMapCache(CacheNames.BOOKS);
        var handler = new CacheConfig().errorHandler();

        assertThatCode(
                        () -> {
                            handler.handleCacheGetError(
                                    new IllegalStateException("down"), cache, 7L);
                            handler.handleCachePutError(
                                    new IllegalStateException("down"), cache, 7L, "value");
                            handler.handleCacheEvictError(
                                    new IllegalStateException("down"), cache, 7L);
                            handler.handleCacheClearError(new IllegalStateException("down"), cache);
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void theLogLineNamesTheKindOfFailureButNotTheValue(CapturedOutput output) {
        Cache cache = new ConcurrentMapCache(CacheNames.BOOKS);

        new CacheConfig()
                .errorHandler()
                .handleCachePutError(
                        new SerializationException("Could not write JSON"),
                        cache,
                        7L,
                        "Ada Lovelace's loan");

        // Without the type, a Redis that is down and a value that cannot be read look the same.
        assertThat(output)
                .contains(
                        "Cache 'books' failed to put entry with key '7'", "SerializationException")
                .doesNotContain("Ada Lovelace");
    }

    private Duration ttlOf(String cacheName) {
        return configOf(cacheName).getTtlFunction().getTimeToLive(null, null);
    }

    private RedisCacheConfiguration configOf(String cacheName) {
        return cacheManager.getCacheConfigurations().get(cacheName);
    }

    private static RedisCacheManager cacheManager() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.builder(connectionFactory)
                        .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig());
        new CacheConfig()
                .libraryCaches(
                        JsonMapper.builder().findAndAddModules().build(),
                        new LibraryCacheProperties(Duration.ofMinutes(10), Duration.ofHours(1)),
                        connectionFactory)
                .customize(builder);
        RedisCacheManager manager = builder.build();
        manager.afterPropertiesSet();
        return manager;
    }
}
