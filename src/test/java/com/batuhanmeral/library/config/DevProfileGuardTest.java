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
 * dev profili prod ile birlikte açılırsa demo üyeyi production veritabanına koyardı; uygulama
 * başlamayı reddeder.
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
     * META-INF/spring.factories'e kayıtlı: her başlangıçta, veritabanına bağlanmadan önce çalışır.
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
