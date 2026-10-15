package com.batuhanmeral.library.common.logging;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Log ayarları ({@code library.logging.*}).
 *
 * @param slowCallThreshold bu kadar süren service çağrısı uyarı olarak log'lanır
 */
@ConfigurationProperties("library.logging")
@Validated
public record LoggingProperties(@NotNull Duration slowCallThreshold) {}
