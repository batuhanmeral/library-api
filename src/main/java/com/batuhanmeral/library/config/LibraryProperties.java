package com.batuhanmeral.library.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kütüphanenin genel ayarları ({@code library.*}); ödünç kuralları LoanProperties'te.
 *
 * @param timeZone kütüphanenin saat dilimi: ödünçler için "bugün" ve gece işinin saati; zaman
 *     damgaları UTC kalır
 * @param scheduling zamanlanmış işler
 */
@ConfigurationProperties("library")
@Validated
public record LibraryProperties(@NotNull ZoneId timeZone, @NotNull @Valid Scheduling scheduling) {

    /**
     * @param enabled zamanlanmış işler kendiliğinden çalışır mı (bkz. SchedulingConfig); testlerde
     *     kapalı
     */
    public record Scheduling(boolean enabled) {}
}
