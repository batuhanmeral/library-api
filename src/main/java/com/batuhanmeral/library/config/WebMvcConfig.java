package com.batuhanmeral.library.config;

import com.batuhanmeral.library.common.logging.RequestLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC ayarlarına ekleme yapar; {@code @EnableWebMvc} Boot'un otomatik ayarını tamamen
 * kapatırdı.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    /** Interceptor yola göre sınırlanabilir: sadece API çağrıları log'lanır. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor()).addPathPatterns("/api/**");
    }
}
