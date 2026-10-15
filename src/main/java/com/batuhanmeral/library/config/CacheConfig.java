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
 * Spring'in cache anotasyonlarını açar ve Redis cache'lerini kurar.
 *
 * <p>Cache advice'ı transaction advice'ının hemen dışında: cache isabeti bağlantı almadan döner,
 * kayıt commit'ten sonra silinir. Hangi depo kullanılacağını spring.cache.type belirler.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    /**
     * Cache sadece okumayı hızlandırır; Redis bozulursa istek düşmez, hata stack trace olmadan
     * log'lanır ve metod cache yokmuş gibi çalışır. Log değeri ya da mesajı yazmaz.
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
     * spring.cache.type redis iken Spring Boot'un kurduğu RedisCacheManager'ı ayarlar: her cache
     * tek tipi düz JSON olarak saklar (Redis'e yazan biri sınıf seçemesin), kayıtlar TTL ile düşer,
     * listede olmayan cache açılmaz, allEntries silmesi KEYS yerine SCAN kullanır.
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
