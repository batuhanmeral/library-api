package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
 * The database users of docker-compose.yml: the roles script (docker/postgres/init) creates an
 * owner, who runs the migrations, and an application user, who may only read and write rows. The
 * application connects with the second, Flyway with the first (spring.flyway.user).
 *
 * <p>A server of its own: the shared one of {@link DatabaseTest} has only its superuser, and its
 * tables belong to that superuser.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseRolesTest {

    private static final String OWNER = "library_owner";
    private static final String APP = "library_app";

    /**
     * The script runs the way the image runs it in docker-compose.yml: once, from
     * /docker-entrypoint-initdb.d, when the data directory is empty.
     */
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DatabaseTest.POSTGRES_IMAGE)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("docker/postgres/init/10-create-roles.sh"),
                            "/docker-entrypoint-initdb.d/10-create-roles.sh")
                    .withEnv("DB_OWNER_USER", OWNER)
                    .withEnv("DB_OWNER_PASSWORD", "owner-test-password")
                    .withEnv("DB_APP_USER", APP)
                    .withEnv("DB_APP_PASSWORD", "app-test-password");

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
     * What an SQL injection through the application could no longer do: change or drop the schema,
     * empty a table in one statement, or create objects of its own.
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
        // SQLState 42501, insufficient_privilege: "permission denied" or "must be owner".
        assertThatThrownBy(() -> jdbcTemplate.execute(statement))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo("42501");
    }
}
