package com.batuhanmeral.library;

import com.batuhanmeral.library.config.LibraryProperties;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Zamanı ileri alan testlerde uygulama saatini değiştirir; {@code @Primary} her {@code Clock}
 * enjeksiyonuna bunu verir.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock(LibraryProperties properties) {
        return new MutableClock(Instant.now(), properties.timeZone());
    }
}
