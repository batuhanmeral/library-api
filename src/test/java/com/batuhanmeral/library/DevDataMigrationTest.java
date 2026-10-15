package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * dev profili db/dev betiklerini de çalıştırır; her veritabanında çalışmalı ve tekrar
 * çalıştırılabilmeli.
 *
 * <p>Ortak PostgreSQL'i kullanmaz: db/dev'i Flyway geçmişine ekler, sonraki context'ler açılmazdı.
 */
@DataJpaTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
class DevDataMigrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DatabaseTest.POSTGRES_IMAGE);

    @Autowired private Flyway flyway;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsTheDemoMemberOnce() {
        flyway.migrate();

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from users where email = 'member@example.com'"
                                        + " and role = 'MEMBER'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theDemoMemberHasAFictionalPhoneForSmsReminders() {
        // 555-0100 ile 555-0199 arası Kuzey Amerika numara planında kurgu için ayrılmış.
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select phone from users where email = 'member@example.com'",
                                String.class))
                .isEqualTo("+15555550100");
    }

    /**
     * Telefon eklenmeden önce migrate edilmiş veritabanı: Flyway değişen betiği tekrar çalıştırır,
     * ikinci üye değil telefon eklenmeli. Flyway kendi bağlantısını kullandığı için test
     * transaction'ı yok.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aDemoMemberFromAnEarlierVersionGetsThePhoneToo() {
        jdbcTemplate.update("update users set phone = null where email = 'member@example.com'");
        jdbcTemplate.update("delete from flyway_schema_history where version is null");

        flyway.migrate();

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select phone from users where email = 'member@example.com'",
                                String.class))
                .isEqualTo("+15555550100");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from users where email = 'member@example.com'",
                                Integer.class))
                .isEqualTo(1);
    }
}
