package com.batuhanmeral.library;

import com.batuhanmeral.library.config.LibraryProperties;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock in tests that move time forward. {@code @Primary} makes every
 * {@code Clock} injection point get this one instead of the clock from ClockConfig.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock(LibraryProperties properties) {
        return new MutableClock(Instant.now(), properties.timeZone());
    }
}
