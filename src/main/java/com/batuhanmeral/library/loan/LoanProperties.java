package com.batuhanmeral.library.loan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ödünç kuralları ({@code library.loan.*}). Değerler başlangıçtan sonra değişmez; eksik ya da
 * geçersiz değer uygulamayı başlatmaz ({@code @Validated}).
 *
 * @param maxActiveLoans bir üyenin aynı anda sahip olabileceği aktif ya da gecikmiş ödünç sayısı
 * @param periodDays ödünç tarihinden teslim tarihine gün sayısı
 * @param overdueCheck gecikmiş ödünçleri işaretleyen gece işi
 */
@ConfigurationProperties("library.loan")
@Validated
public record LoanProperties(
        @Min(1) int maxActiveLoans,
        @Min(1) @Max(365) int periodDays,
        @NotNull @Valid OverdueCheck overdueCheck) {

    /**
     * @param cron işin çalışma zamanı, library.time-zone'a göre okunan Spring cron ifadesi
     * @param batchSize işin bir transaction'da işlediği üye sayısı
     */
    public record OverdueCheck(@NotBlank String cron, @Min(1) @Max(1000) int batchSize) {}
}
