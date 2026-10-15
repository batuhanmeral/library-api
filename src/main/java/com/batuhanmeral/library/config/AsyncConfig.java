package com.batuhanmeral.library.config;

import com.batuhanmeral.library.common.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async}'i açar: metod Spring Boot'un task executor'ında çalışır, çağrı hemen döner. Bu
 * olmadan anotasyon yok sayılır.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    /**
     * Spring Boot bunu kendi executor'ına uygular: işler, onları başlatan isteğin request id'sini
     * taşır.
     */
    @Bean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
