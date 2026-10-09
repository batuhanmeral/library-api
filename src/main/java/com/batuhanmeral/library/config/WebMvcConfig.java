package com.batuhanmeral.library.config;

import com.batuhanmeral.library.common.logging.RequestLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Adds to Spring MVC's configuration without replacing it. A WebMvcConfigurer only contributes;
 * {@code @EnableWebMvc} would switch off Spring Boot's MVC auto-configuration altogether.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    /** Interceptors, unlike filters, can be limited to paths: only API calls are logged. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor()).addPathPatterns("/api/**");
    }
}
