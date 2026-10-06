package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LibraryApplicationTests {

    @Autowired private Clock clock;

    @Test
    void contextLoads() {}

    @Test
    void theLibraryRunsOnIstanbulTime() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }
}
