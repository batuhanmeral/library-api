package com.batuhanmeral.library.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cache kayıtlarının ömrü ({@code library.cache.*}). API'nin her değişikliği ilgili kaydı hemen
 * siler; TTL sadece başka yoldan yapılan değişikliklerde kaydın ne kadar yanlış kalabileceğini
 * sınırlar.
 *
 * @param booksTtl GET /books/{id} kayıtları
 * @param categoriesTtl kategori listesi
 */
@ConfigurationProperties("library.cache")
@Validated
public record LibraryCacheProperties(@NotNull Duration booksTtl, @NotNull Duration categoriesTtl) {}
