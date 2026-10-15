package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.category.CategoryRepository;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Gerçek Redis'e karşı cache. Sadece Redis'in gösterdiklerini sınar: saklanan baytlar (düz JSON),
 * anahtar başına TTL ve cevap vermeyen Redis.
 *
 * <p>Testcontainers'ın Redis sınıfı yok; portlu GenericContainer yeter. {@code name = "redis"}
 * Spring Boot'a hangi bağlantı bilgisini kuracağını söyler.
 */
@SpringBootTest(properties = "spring.cache.type=redis")
@AutoConfigureMockMvc
class RedisCacheTest implements DatabaseTest {

    /** redis:8.10.3-alpine, docker-compose.yml'deki gibi digest ile sabit. */
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                            DockerImageName.parse(
                                    "redis@sha256:7958cfbb9d152f87dadfbc89f1b0b15938f0b81ff8982feb327476f4911935c3"))
                    .withExposedPorts(6379);

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private StringRedisTemplate redis;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private CategoryRepository categoryRepository;

    @Autowired private MeterRegistry meterRegistry;

    private long bookId;

    @BeforeEach
    void emptyRedisAndSaveABook() {
        redis.execute(
                connection -> {
                    connection.serverCommands().flushAll();
                    return null;
                },
                true);
        Author author = authorRepository.save(new Author("Frank Herbert"));
        Book book = new Book("Dune", 2);
        book.setAuthor(author);
        bookId = bookRepository.save(book).getId();
    }

    @Test
    void aBookIsStoredAsPlainJsonWithItsTimeToLive() {
        assertThat(getBook()).hasStatus(HttpStatus.OK);

        String key = "library-api:books::" + bookId;
        String stored = redis.opsForValue().get(key);
        assertThat(stored).isNotNull();
        assertThat((String) JsonPath.read(stored, "$.title")).isEqualTo("Dune");
        assertThat((String) JsonPath.read(stored, "$.author.name")).isEqualTo("Frank Herbert");
        // Cache başına tip: değerde Redis'e yazanın seçebileceği sınıf adı yok.
        assertThat(stored).doesNotContain("@class").doesNotContain("com.batuhanmeral");
        // library.cache.books-ttl 10 dakika; Redis kendisi geri sayar.
        assertThat(redis.getExpire(key)).isBetween(1L, Duration.ofMinutes(10).toSeconds());
    }

    @Test
    void theSecondReadComesFromRedisAndAChangeThroughTheApiDropsIt() {
        assertThat(titleOf(getBook())).isEqualTo("Dune");
        // Uygulamanın arkasından değişiklik: cache'teki kayıt görmez.
        jdbcTemplate.update("update books set title = 'Changed in SQL' where id = ?", bookId);

        assertThat(titleOf(getBook())).isEqualTo("Dune");

        MvcTestResult patch =
                mvc.patch()
                        .uri("/api/v1/books/{id}", bookId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Dune Messiah\"}")
                        .exchange();
        assertThat(patch).hasStatus(HttpStatus.OK);

        assertThat(redis.hasKey("library-api:books::" + bookId)).isFalse();
        assertThat(titleOf(getBook())).isEqualTo("Dune Messiah");
    }

    @Test
    void theCategoryListIsOneKeyThatLivesAnHour() {
        categoryRepository.save(new Category("Science", null));

        assertThat(mvc.get().uri("/api/v1/categories").exchange()).hasStatus(HttpStatus.OK);

        String stored = redis.opsForValue().get("library-api:categories::all");
        assertThat((String) JsonPath.read(stored, "$[0].name")).isEqualTo("Science");
        assertThat(redis.getExpire("library-api:categories::all"))
                .isBetween(Duration.ofMinutes(10).toSeconds() + 1, Duration.ofHours(1).toSeconds());
    }

    /**
     * spring.cache.redis.enable-statistics: /actuator/metrics için cache başına isabet ve ıskalar.
     */
    @Test
    void hitsAndMissesAreCountedPerCache() {
        double hits = gets("hit");
        double misses = gets("miss");

        getBook();
        getBook();
        getBook();

        assertThat(gets("miss")).isEqualTo(misses + 1);
        assertThat(gets("hit")).isEqualTo(hits + 2);
    }

    /**
     * Redis duraklatılır (port açık kalır), komutlar timeout'a düşer: en kötü durum. İstek 500 ms
     * sonra veritabanından cevaplanır.
     */
    @Test
    void aRedisThatStopsAnsweringSlowsReadsDownButDoesNotBreakThem() {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            long start = System.nanoTime();
            MvcTestResult result = getBook();
            Duration took = Duration.ofNanos(System.nanoTime() - start);

            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(titleOf(result)).isEqualTo("Dune");
            assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(500));
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    private double gets(String result) {
        return meterRegistry
                .get("cache.gets")
                .tag("cache", CacheNames.BOOKS)
                .tag("result", result)
                .functionCounter()
                .count();
    }

    private MvcTestResult getBook() {
        return mvc.get().uri("/api/v1/books/{id}", bookId).exchange();
    }

    private static String titleOf(MvcTestResult result) {
        String body =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.title");
    }
}
