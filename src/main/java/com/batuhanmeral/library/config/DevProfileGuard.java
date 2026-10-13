package com.batuhanmeral.library.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start with the dev and prod profiles together. The dev profile runs the demo data
 * migration (db/dev, a member with a well-known address) and reads .env from the working directory;
 * turned on next to prod by mistake (SPRING_PROFILES_ACTIVE=prod,dev), it would put the demo member
 * into the production database.
 *
 * <p>An EnvironmentPostProcessor, registered in META-INF/spring.factories, runs while the
 * environment is prepared: after the profiles are known (it has the default order, after Spring
 * Boot's ConfigDataEnvironmentPostProcessor), before any bean exists. A bean with the same check
 * could come too late, after Flyway has already run the migrations.
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
