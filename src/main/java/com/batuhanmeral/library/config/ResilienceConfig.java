package com.batuhanmeral.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Spring Framework'ün {@code @Retryable} ve {@code @ConcurrencyLimit} anotasyonlarını açar. Proxy
 * ile çalışır: sadece başka bean'den gelen çağrılar.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ResilienceConfig {}
