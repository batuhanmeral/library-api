package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Bir veritabanında uygulama iki kez olabilir: compose dev'i public, uygulama container'ını "app"
 * şemasında çalıştırır. pg_trgm gibi extension veritabanı başına birdir; migration'lar hangi şema
 * önce gelirse gelsin çalışmalı.
 *
 * <p>Sıra belli olsun diye kendi sunucusu var; Flyway Spring olmadan çalıştırılır.
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
