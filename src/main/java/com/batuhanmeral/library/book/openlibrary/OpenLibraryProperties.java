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
 * Open Library bağlantısı ({@code library.open-library.*}). İki timeout da zorunlu: yoksa JDK
 * istemcisi sonsuza kadar bekler.
 *
 * @param baseUrl API adresi, https://openlibrary.org; testler başka yere yönlendirir
 * @param connectTimeout bağlantı için bekleme süresi
 * @param readTimeout bağlandıktan sonra cevap için bekleme süresi
 * @param userAgent uygulamanın kendini tanıttığı ad; kişisel iletişim bilgisi yok
 */
@ConfigurationProperties("library.open-library")
@Validated
public record OpenLibraryProperties(
        @NotNull URI baseUrl,
        @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30) Duration readTimeout,
        @NotBlank String userAgent) {}
