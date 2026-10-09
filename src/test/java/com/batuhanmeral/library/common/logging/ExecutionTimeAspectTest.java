package com.batuhanmeral.library.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * The aspect without a Spring context: AspectJProxyFactory builds the same kind of proxy Spring
 * builds for a bean, around a plain object, with only this aspect in it.
 */
@ExtendWith(OutputCaptureExtension.class)
class ExecutionTimeAspectTest {

    private static final Duration NEVER_SLOW = Duration.ofHours(1);

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @BeforeEach
    void showDebugLines() {
        loggingSystem.setLogLevel(ExecutionTimeAspect.class.getName(), LogLevel.DEBUG);
    }

    @AfterEach
    void restoreLogLevel() {
        loggingSystem.setLogLevel(ExecutionTimeAspect.class.getName(), null);
    }

    @Test
    void aCallIsLoggedWithItsDuration(CapturedOutput output) {
        SampleService service = proxy(new SampleService(), NEVER_SLOW);

        assertThat(service.work("Dune")).isEqualTo("done: Dune");

        assertThat(output).containsPattern("DEBUG .* SampleService\\.work took \\d+ ms");
        assertThat(output).doesNotContain("WARN");
    }

    @Test
    void argumentsAndResultsAreNotLogged(CapturedOutput output) {
        proxy(new SampleService(), NEVER_SLOW).work("secret title");

        assertThat(output).contains("SampleService.work took").doesNotContain("secret");
    }

    @Test
    void aCallThatTakesAtLeastTheThresholdIsAWarning(CapturedOutput output) {
        proxy(new SampleService(), Duration.ZERO).work("Dune");

        assertThat(output).containsPattern("WARN .* Slow call: SampleService\\.work took \\d+ ms");
    }

    @Test
    void aFailedCallIsLoggedAndItsExceptionReachesTheCaller(CapturedOutput output) {
        SampleService service = proxy(new SampleService(), NEVER_SLOW);

        assertThatThrownBy(service::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(output)
                .containsPattern(
                        "SampleService\\.fail failed after \\d+ ms \\(IllegalStateException\\)")
                .doesNotContain("boom");
    }

    /** The pointcut picks classes annotated with {@code @Service}; other beans are left alone. */
    @Test
    void aClassThatIsNotAServiceIsNotTimed(CapturedOutput output) {
        proxy(new SampleComponent(), NEVER_SLOW).work();

        assertThat(output).doesNotContain("SampleComponent.work");
    }

    private static <T> T proxy(T target, Duration slowCallThreshold) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new ExecutionTimeAspect(new LoggingProperties(slowCallThreshold)));
        return factory.getProxy();
    }

    @Service
    static class SampleService {

        public String work(String title) {
            return "done: " + title;
        }

        public void fail() {
            throw new IllegalStateException("boom");
        }
    }

    @Component
    static class SampleComponent {

        public void work() {}
    }
}
