package com.batuhanmeral.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Turns on Spring Framework's own {@code @Retryable} (and {@code @ConcurrencyLimit}). Without this
 * the annotations are only metadata and nothing is retried. Like {@code @Transactional}, they work
 * through a proxy: only calls that come from another bean are retried.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ResilienceConfig {}
