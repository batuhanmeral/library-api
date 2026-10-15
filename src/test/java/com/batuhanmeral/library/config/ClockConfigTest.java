package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Uygulama saati makinenin değil library.time-zone'un dilimindedir. */
class ClockConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void theClockUsesTheConfiguredTimeZone() {
        runner.withPropertyValues("library.time-zone=Asia/Tokyo", "library.scheduling.enabled=true")
                .run(context ->
                        assertThat(context.getBean(Clock.class).getZone())
                                .isEqualTo(ZoneId.of("Asia/Tokyo")));
    }

    @Test
    void theApplicationDoesNotStartWithoutAValidTimeZone() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "library.time-zone=Mars/Olympus", "library.scheduling.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LibraryProperties.class)
    @Import(ClockConfig.class)
    static class Config {}
}
