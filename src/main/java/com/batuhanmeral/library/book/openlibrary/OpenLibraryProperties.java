package com.batuhanmeral.library.book.openlibrary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection to Open Library, bound from {@code library.open-library.*}. Both timeouts are
 * required: without them the JDK HTTP client waits for an answer forever, and every waiting call
 * holds a request thread of this application.
 *
 * @param baseUrl where the API is, https://openlibrary.org; tests point it elsewhere
 * @param connectTimeout how long to wait for the connection
 * @param readTimeout how long to wait for the answer once connected
 * @param userAgent how the application introduces itself; no personal contact details
 */
@ConfigurationProperties("library.open-library")
@Validated
public record OpenLibraryProperties(
        @NotNull URI baseUrl,
        @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30) Duration readTimeout,
        @NotBlank String userAgent) {}
