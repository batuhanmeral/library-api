package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Both profiles connect twice: Flyway as the owner of the schema, the application as a user that
 * may only read and write rows (DatabaseRolesTest checks the rights). The files are read instead of
 * starting the application with a profile (see ProdLoggingSettingsTest).
 */
class DatabaseUserSettingsTest {

    @ParameterizedTest
    @CsvSource({
        "dev, spring.flyway.user, DB_OWNER_USER",
        "dev, spring.flyway.password, DB_OWNER_PASSWORD",
        "dev, spring.datasource.username, DB_APP_USER",
        "dev, spring.datasource.password, DB_APP_PASSWORD",
        "prod, spring.flyway.user, DB_MIGRATION_USERNAME",
        "prod, spring.flyway.password, DB_MIGRATION_PASSWORD",
        "prod, spring.datasource.username, DB_USERNAME",
        "prod, spring.datasource.password, DB_PASSWORD"
    })
    void eachConnectionTakesItsOwnUser(String profile, String key, String variable)
            throws IOException {
        MockEnvironment environment =
                new MockEnvironment().withProperty(variable, "value-of-" + variable);

        PropertySource<?> settings =
                new YamlPropertySourceLoader()
                        .load(profile, new ClassPathResource("application-" + profile + ".yml"))
                        .getFirst();

        assertThat(environment.resolvePlaceholders(String.valueOf(settings.getProperty(key))))
                .isEqualTo("value-of-" + variable);
    }
}
