package com.batuhanmeral.library;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * What a test that needs the database implements: a real PostgreSQL, the same image as
 * docker-compose.yml, so the tests see what the application sees (partial indexes, row locks,
 * PostgreSQL's own error messages), and empty tables at the start of every test.
 *
 * <p>Spring Boot finds the {@code @ServiceConnection} field on the interfaces of the test class,
 * starts the container the first time a test context needs it and points the DataSource at it (no
 * spring.datasource.* properties). The container is not a bean of any context, so closing a context
 * does not stop it: one container serves every context of the test run, and Testcontainers removes
 * it when the JVM exits.
 *
 * <p>One server for all contexts also means one database. On H2 every context had a database of its
 * own; here rows that one test class commits are seen by the next class. So no test trusts what it
 * finds: {@link #deleteAllRows} empties the tables before each test.
 */
public interface DatabaseTest {

    /**
     * postgres:18.4-alpine, pinned by digest like in docker-compose.yml. Testcontainers cannot
     * parse a name with both a tag and a digest, so the tag is only in this comment.
     */
    DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(
                    "postgres@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15");

    /**
     * Every cached test context keeps its own connection pool open (the test application.yml makes
     * them small). About twenty contexts share this server, more than the default limit of 100
     * connections allows, so the limit is raised. fsync=off is what Testcontainers sets by default:
     * a test database does not need to survive a crash.
     */
    @ServiceConnection
    PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(POSTGRES_IMAGE)
                    .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=200");

    /**
     * Runs before the test class's own {@code @BeforeEach} methods (JUnit runs inherited ones
     * first), so a test's setup always starts from empty tables. In a test that runs in a
     * transaction (@DataJpaTest, @Transactional) the deletes are rolled back with the test: the
     * rows come back afterwards, and the next test deletes them again. The Flyway history is kept.
     */
    @BeforeEach
    default void deleteAllRows(@Autowired JdbcTemplate jdbcTemplate) {
        // Children before parents, so no foreign key is violated.
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
