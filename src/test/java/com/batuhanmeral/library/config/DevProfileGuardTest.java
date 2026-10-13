package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.LibraryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

/**
 * The dev profile adds the demo data (db/dev) and reads .env. Turned on next to prod by mistake, it
 * would create the demo member in the production database; the application refuses to start
 * instead.
 */
class DevProfileGuardTest {

    private final DevProfileGuard guard = new DevProfileGuard();

    @Test
    void devAndProdTogetherAreRefused() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "dev");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev and prod");
    }

    @Test
    void eachProfileAloneIsAccepted() {
        for (String profile : new String[] {"dev", "prod"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);

            assertThatCode(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                    .doesNotThrowAnyException();
        }
    }

    /**
     * Registered in META-INF/spring.factories, so it runs on every start, after the profiles are
     * known and before anything connects to the database.
     */
    @Test
    void theApplicationStopsBeforeItConnectsAnywhere() {
        SpringApplicationBuilder application =
                new SpringApplicationBuilder(LibraryApplication.class)
                        .web(WebApplicationType.NONE)
                        .profiles("dev", "prod");

        assertThatThrownBy(application::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev and prod");
    }
}
