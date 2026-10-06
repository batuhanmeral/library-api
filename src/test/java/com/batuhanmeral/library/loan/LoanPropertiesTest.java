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
        runner.withPropertyValues(
                        "library.loan.max-active-loans=5",
                        "library.loan.period-days=21",
                        "library.loan.overdue-check.cron=0 30 3 * * *",
                        "library.loan.overdue-check.batch-size=50")
                .run(context -> {
                    LoanProperties properties = context.getBean(LoanProperties.class);
                    assertThat(properties.maxActiveLoans()).isEqualTo(5);
                    assertThat(properties.periodDays()).isEqualTo(21);
                    assertThat(properties.overdueCheck().cron()).isEqualTo("0 30 3 * * *");
                    assertThat(properties.overdueCheck().batchSize()).isEqualTo(50);
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
        runner.withPropertyValues(
                        "library.loan.max-active-loans=3",
                        "library.loan.period-days=14",
                        "library.loan.overdue-check.cron=0 0 2 * * *",
                        "library.loan.overdue-check.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoanProperties.class)
    static class Config {}
}
