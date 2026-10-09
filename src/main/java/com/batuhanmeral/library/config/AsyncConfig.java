package com.batuhanmeral.library.config;

import com.batuhanmeral.library.common.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on {@code @Async}: a call to such a method returns at once and the method runs on the task
 * executor Spring Boot configures (applicationTaskExecutor, spring.task.execution.*). Without this
 * the annotation is ignored and the method runs in the caller's thread.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    /**
     * Spring Boot applies this to the executor it configures: tasks keep the request id of the
     * request that started them.
     */
    @Bean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
