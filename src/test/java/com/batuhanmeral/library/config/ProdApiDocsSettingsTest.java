package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Production'da API_DOCS_ENABLED=true olmadıkça API açıklaması ve Swagger UI kapalı. Dosya okunur,
 * uygulama başlatılmaz.
 */
class ProdApiDocsSettingsTest {

    @Test
    void theProdProfileTurnsTheApiDocsOffByDefault() throws IOException {
        PropertySource<?> prod = prodProfile();

        assertThat(resolve(prod, "springdoc.api-docs.enabled", new StandardEnvironment()))
                .isEqualTo("false");
        assertThat(resolve(prod, "springdoc.swagger-ui.enabled", new StandardEnvironment()))
                .isEqualTo("false");
    }

    @Test
    void anEnvironmentVariableTurnsThemOn() throws IOException {
        MockEnvironment environment =
                new MockEnvironment().withProperty("API_DOCS_ENABLED", "true");

        assertThat(resolve(prodProfile(), "springdoc.api-docs.enabled", environment))
                .isEqualTo("true");
    }

    private static PropertySource<?> prodProfile() throws IOException {
        return new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"))
                .getFirst();
    }

    private static String resolve(
            PropertySource<?> source, String key, ConfigurableEnvironment environment) {
        return environment.resolvePlaceholders(String.valueOf(source.getProperty(key)));
    }
}
