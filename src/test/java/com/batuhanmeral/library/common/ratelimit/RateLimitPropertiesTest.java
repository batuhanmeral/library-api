package com.batuhanmeral.library.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Bir saniyeden kısa pencere sayacı hemen silerdi; uygulama böyle bir ayarla başlamaz. */
class RateLimitPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT0.5S", "PT-1M"})
    void aWindowUnderOneSecondIsRefused(String window) {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule(10, Duration.parse(window));

        assertThat(validator.validate(rule)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT1S", "PT1M", "PT1H"})
    void aWindowOfASecondOrMoreIsAccepted(String window) {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule(10, Duration.parse(window));

        assertThat(validator.validate(rule)).isEmpty();
    }
}
