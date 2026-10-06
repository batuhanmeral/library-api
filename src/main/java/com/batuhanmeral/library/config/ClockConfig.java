package com.batuhanmeral.library.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Today" comes from this clock instead of LocalDate.now(), so a test can replace it with a fixed
 * one (Clock.fixed) and check date rules without waiting for a date to pass. The clock runs in the
 * library's time zone, not the machine's: a server or container on UTC would otherwise see a
 * different date than the library between midnight in Istanbul and midnight in UTC.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock(LibraryProperties properties) {
        return Clock.system(properties.timeZone());
    }
}
