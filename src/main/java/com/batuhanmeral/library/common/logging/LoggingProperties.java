package com.batuhanmeral.library.common.logging;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Logging settings of the library, bound from {@code library.logging.*}.
 *
 * @param slowCallThreshold a service call that takes at least this long is logged as a warning
 */
@ConfigurationProperties("library.logging")
@Validated
public record LoggingProperties(@NotNull Duration slowCallThreshold) {}
