package com.batuhanmeral.library;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Veritabanı isteyen testlerin implement ettiği arayüz: docker-compose.yml ile aynı imajda gerçek
 * PostgreSQL ve her testin başında boş tablolar.
 *
 * <p>Spring Boot {@code @ServiceConnection} alanını bulur ve container'ı ilk ihtiyaçta başlatır.
 * Container hiçbir context'in bean'i değil, tüm test çalışmasında tek container kullanılır. Tek
 * veritabanı olduğu için {@link #deleteAllRows} her testten önce tabloları boşaltır.
 */
public interface DatabaseTest {

    /**
     * postgres:18.6-alpine, digest ile sabit. Testcontainers tag ve digest'i birlikte okuyamaz, tag
     * sadece bu yorumda.
     */
    DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(
                    "postgres@sha256:9ba53c7d41ea093baf5c1ba635ba5434c4f979880527d49bb7d9237d3193085b");

    /**
     * Her cache'lenen context kendi bağlantı havuzunu tutar; yirmi kadar context için 100 bağlantı
     * yetmez, sınır yükseltildi. fsync=off Testcontainers varsayılanı.
     */
    @ServiceConnection
    PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(POSTGRES_IMAGE)
                    .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=200");

    /**
     * Test sınıfının kendi {@code @BeforeEach} metodlarından önce çalışır. Transaction'lı testte
     * silmeler testle birlikte geri alınır; Flyway geçmişi korunur.
     */
    @BeforeEach
    default void deleteAllRows(@Autowired JdbcTemplate jdbcTemplate) {
        // Foreign key bozulmasın diye önce çocuklar.
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate,
                "loans",
                "book_categories",
                "books",
                "authors",
                "categories",
                "users");
    }
}
