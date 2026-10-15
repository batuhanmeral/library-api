package com.batuhanmeral.library.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * prod profiliyle başlatmak tüm test JVM'inin log'unu JSON yapardı, bu yüzden profil dosyası
 * okunur.
 */
class ProdLoggingSettingsTest {

    @Test
    void theProdProfileWritesJsonLogLines() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader()
                        .load("prod", new ClassPathResource("application-prod.yml"));

        // Elastic Common Schema: satır başına bir JSON nesnesi, MDC değerleri dahil.
        assertThat(sources.getFirst().getProperty("logging.structured.format.console"))
                .hasToString("ecs");
    }
}
