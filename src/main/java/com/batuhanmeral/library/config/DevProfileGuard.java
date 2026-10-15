package com.batuhanmeral.library.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * dev ve prod profilleri birlikteyken açılmayı reddeder: dev demo üyeyi production veritabanına
 * koyardı.
 *
 * <p>EnvironmentPostProcessor (META-INF/spring.factories): profiller belli olduktan sonra, hiçbir
 * bean yokken çalışır. Bean olsaydı Flyway'den sonraya kalabilirdi.
 */
public class DevProfileGuard implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.matchesProfiles("dev & prod")) {
            throw new IllegalStateException(
                    "The dev and prod profiles cannot be active together: dev adds demo data"
                            + " (db/dev) to the database.");
        }
    }
}
