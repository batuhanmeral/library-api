package com.batuhanmeral.library.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Today" comes from this clock instead of LocalDate.now(), so a test can replace it with a fixed
 * one (Clock.fixed) and check date rules without waiting for a date to pass.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
