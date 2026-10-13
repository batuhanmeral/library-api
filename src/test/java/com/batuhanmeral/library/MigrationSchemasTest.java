package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One database can hold the application twice: docker-compose.yml runs the dev profile in the
 * public schema and the application container in the "app" schema. Tables belong to a schema, but
 * an extension such as pg_trgm exists once per database, in the schema it was created in; the
 * migrations must work whichever schema comes first.
 *
 * <p>A server of its own, so the order is known; Flyway is run directly, without Spring.
 */
class MigrationSchemasTest {

    @Test
    void theMigrationsRunInASecondSchemaOfTheSameDatabase() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DatabaseTest.POSTGRES_IMAGE)) {
            postgres.start();

            MigrateResult app = migrate(postgres, "app");
            MigrateResult publicSchema = migrate(postgres, "public");

            assertThat(app.success).isTrue();
            assertThat(publicSchema.success).isTrue();
            assertThat(publicSchema.migrationsExecuted).isEqualTo(app.migrationsExecuted);
        }
    }

    private static MigrateResult migrate(PostgreSQLContainer postgres, String schema) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
