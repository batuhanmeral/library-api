package com.batuhanmeral.library.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} methods, unless library.scheduling.enabled is false. It is off in the
 * tests (src/test/resources/config/application.yml): a job that starts by itself in the middle of a
 * test would change data the test is checking. Tests run the jobs directly instead.
 *
 * <p>With {@code @EnableScheduling} on LibraryApplication there would be no way to turn it off.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("library.scheduling.enabled")
public class SchedulingConfig {}
