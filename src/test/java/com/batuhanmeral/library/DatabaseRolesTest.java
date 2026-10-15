package com.batuhanmeral.library;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * docker-compose.yml'deki veritabanı kullanıcıları: migration'ları çalıştıran şema sahibi ve sadece
 * satır okuyup yazan uygulama kullanıcısı.
 *
 * <p>Kendi sunucusu var: {@link DatabaseTest}'in ortak sunucusunda sadece superuser var.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseRolesTest {

    private static final String OWNER = "library_owner";
    private static final String APP = "library_app";

    /**
     * Betik, docker-compose.yml'deki imajın çalıştırdığı gibi çalışır: veri dizini boşken bir kez.
     */
    static final PostgreSQLContainer POSTGRES = withRolesScript("owner-test-password");

    private static PostgreSQLContainer withRolesScript(String ownerPassword) {
        return new PostgreSQLContainer(DatabaseTest.POSTGRES_IMAGE)
                .withCopyFileToContainer(
                        MountableFile.forHostPath("docker/postgres/init/10-create-roles.sh"),
                        "/docker-entrypoint-initdb.d/10-create-roles.sh")
                .withEnv("DB_OWNER_USER", OWNER)
                .withEnv("DB_OWNER_PASSWORD", ownerPassword)
                .withEnv("DB_APP_USER", APP)
                .withEnv("DB_APP_PASSWORD", "app-test-password");
    }

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP);
        registry.add("spring.datasource.password", () -> "app-test-password");
        registry.add("spring.flyway.user", () -> OWNER);
        registry.add("spring.flyway.password", () -> "owner-test-password");
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Test
    void theApplicationConnectsAsTheApplicationUser() {
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo(APP);
    }

    @Test
    void theMigrationsRanAsTheOwnerAndTheTablesBelongToIt() {
        assertThat(
                        jdbcTemplate.queryForList(
                                "select distinct tableowner from pg_tables"
                                        + " where schemaname = 'public'",
                                String.class))
                .containsExactly(OWNER);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from flyway_schema_history where success",
                                Integer.class))
                .isPositive();
    }

    @Test
    void theApplicationUserReadsAndWritesRows() {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/categories")
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"Science\"}")
                                .exchange())
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/api/v1/categories").exchange()).hasStatus(HttpStatus.OK);
        assertThat(jdbcTemplate.update("delete from categories where name = 'Science'"))
                .isEqualTo(1);
    }

    /**
     * Uygulama üzerinden SQL injection artık bunları yapamaz: şemayı değiştirmek, tabloyu tek
     * komutla boşaltmak, kendi nesnesini oluşturmak.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "drop table loans",
                "alter table users add column note varchar(10)",
                "truncate table books cascade",
                "create table notes (id int)",
                "create schema scratch",
                "create temporary table notes (id int)"
            })
    void theApplicationUserCannotChangeTheSchema(String statement) {
        // SQLState 42501, insufficient_privilege.
        assertThatThrownBy(() -> jdbcTemplate.execute(statement))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo("42501");
    }

    /** Sunucunun kendi "postgres" veritabanı da iki kullanıcıya kapalı. */
    @Test
    void theApplicationUserCannotConnectToTheServersOtherDatabase() {
        String url = POSTGRES.getJdbcUrl().replaceFirst("/test\\b", "/postgres");

        assertThatThrownBy(() -> DriverManager.getConnection(url, APP, "app-test-password").close())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied for database");
    }

    /**
     * Betiğin başındaki kontroller psql'den önce çalışır, burada sh ile denenebilir. İlk başlatma,
     * şifresi herkesçe bilinen bir sahip oluşturmak yerine durur.
     */
    @ParameterizedTest
    @CsvSource({
        "library_owner, change-me, library_app, app-pass, DB_OWNER_PASSWORD is still change-me",
        "library_owner, owner-pass, library_app, change-me, DB_APP_PASSWORD is still change-me",
        "test, owner-pass, library_app, app-pass, DB_OWNER_USER must differ from POSTGRES_USER",
        "library_owner, owner-pass, library_owner, app-pass, DB_APP_USER must differ from DB_OWNER_USER"
    })
    void theScriptRefusesAPlaceholderPasswordOrASharedName(
            String owner, String ownerPassword, String app, String appPassword, String message)
            throws Exception {
        ProcessBuilder script =
                new ProcessBuilder("sh", "docker/postgres/init/10-create-roles.sh")
                        .redirectErrorStream(true);
        script.environment()
                .putAll(
                        Map.of(
                                "POSTGRES_USER", "test",
                                "POSTGRES_DB", "test",
                                "DB_OWNER_USER", owner,
                                "DB_OWNER_PASSWORD", ownerPassword,
                                "DB_APP_USER", app,
                                "DB_APP_PASSWORD", appPassword));
        Process process = script.start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);

        assertThat(process.waitFor()).isEqualTo(1);
        assertThat(output).contains(message);
    }
}
