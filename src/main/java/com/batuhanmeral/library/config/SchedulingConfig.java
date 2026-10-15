package com.batuhanmeral.library.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} metodları açar; library.scheduling.enabled false ise kapalı. Testlerde
 * kapalıdır, kendiliğinden başlayan iş test verisini değiştirmesin.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("library.scheduling.enabled")
public class SchedulingConfig {}
