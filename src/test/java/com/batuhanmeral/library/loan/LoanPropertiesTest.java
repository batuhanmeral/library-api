package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Binding and validation of library.loan.* in a small context, without starting the application.
 * ApplicationContextRunner starts a fresh context for each run with exactly the given properties.
 */
class LoanPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void bindsKebabCasePropertiesToTheRecord() {
        runner.withPropertyValues("library.loan.max-active-loans=5", "library.loan.period-days=21")
                .run(context -> {
                    LoanProperties properties = context.getBean(LoanProperties.class);
                    assertThat(properties.maxActiveLoans()).isEqualTo(5);
                    assertThat(properties.periodDays()).isEqualTo(21);
                });
    }

    @Test
    void theApplicationDoesNotStartWithAMissingOrInvalidValue() {
        runner.withPropertyValues("library.loan.max-active-loans=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("library.loan");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoanProperties.class)
    static class Config {}
}
