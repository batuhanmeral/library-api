package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;

@SpringBootTest
class LibraryApplicationTests {

    @Autowired private Clock clock;

    @Autowired private ApplicationContext context;

    @Test
    void contextLoads() {}

    @Test
    void theLibraryRunsOnIstanbulTime() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    void scheduledJobsAreOffInTests() {
        // library.scheduling.enabled=false (src/test/resources/config/application.yml), so
        // @EnableScheduling is not applied and nothing registers scheduled tasks.
        assertThat(context.getBeanProvider(ScheduledTaskHolder.class).getIfAvailable()).isNull();
    }
}
