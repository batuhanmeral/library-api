package com.batuhanmeral.library.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Bugün" bu saatten gelir, testler Clock.fixed ile değiştirebilir. Saat makinenin değil
 * kütüphanenin saat diliminde çalışır.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock(LibraryProperties properties) {
        return Clock.system(properties.timeZone());
    }
}
