package com.batuhanmeral.library.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How long cache entries live, bound from {@code library.cache.*}. Every change the API makes drops
 * the entries it affects at once; the time to live only limits how long an entry can be wrong after
 * a change made some other way (directly in the database, or by a race between a read and a write).
 *
 * @param booksTtl entries of GET /books/{id}
 * @param categoriesTtl the category list
 */
@ConfigurationProperties("library.cache")
@Validated
public record LibraryCacheProperties(@NotNull Duration booksTtl, @NotNull Duration categoriesTtl) {}
