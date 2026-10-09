package com.batuhanmeral.library.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Starting the application with the prod profile would switch the logging of the whole test JVM to
 * JSON, so this reads the profile's file instead.
 */
class ProdLoggingSettingsTest {

    @Test
    void theProdProfileWritesJsonLogLines() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader()
                        .load("prod", new ClassPathResource("application-prod.yml"));

        // Elastic Common Schema: one JSON object per line, MDC values (requestId) included, line
        // breaks in messages escaped.
        assertThat(sources.getFirst().getProperty("logging.structured.format.console"))
                .hasToString("ecs");
    }
}
