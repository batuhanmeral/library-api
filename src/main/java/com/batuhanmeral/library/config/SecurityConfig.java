package com.batuhanmeral.library.config;

import com.batuhanmeral.library.auth.JwtAuthenticationFilter;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Kim neyi çağırabilir ve çağıran kim olduğunu nasıl kanıtlar. Bu bean Spring Boot'un varsayılan
 * zincirini kapatır.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String API = "/api/v1";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, SecurityErrorHandler errors)
            throws Exception {
        http
                // CSRF kapalı: kullanıcı tarayıcının kendiliğinden eklemediği Authorization
                // header'ından okunur, cookie yok.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // HTTP session yok: her istek kendi token'ını getirir.
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, errors),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                // Kurallar yukarıdan aşağı denenir, ilk eşleşen karar verir.
                .authorizeHttpRequests(auth ->
                        auth
                                // Tomcat'in hata sonrası yönlendirdiği sayfalar; asıl istek zaten
                                // kontrol edildi.
                                .dispatcherTypeMatchers(DispatcherType.ERROR)
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        API + "/auth/register",
                                        API + "/auth/login")
                                .permitAll()
                                // Katalog: herkes okur, admin değiştirir.
                                .requestMatchers(
                                        HttpMethod.GET,
                                        API + "/categories/**",
                                        API + "/authors/**",
                                        API + "/books/**")
                                .permitAll()
                                .requestMatchers(
                                        API + "/categories/**",
                                        API + "/authors/**",
                                        API + "/books/**")
                                .hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, API + "/users/me")
                                .authenticated()
                                .requestMatchers(HttpMethod.GET, API + "/users")
                                .hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, API + "/loans")
                                .hasRole("MEMBER")
                                .requestMatchers(HttpMethod.GET, API + "/loans/me")
                                .hasRole("MEMBER")
                                .requestMatchers(HttpMethod.GET, API + "/loans")
                                .hasRole("ADMIN")
                                // Sahibi ya da admin; sahipliği LoanService kontrol eder.
                                .requestMatchers(HttpMethod.GET, API + "/loans/*")
                                .authenticated()
                                .requestMatchers(HttpMethod.POST, API + "/loans/*/return")
                                .authenticated()
                                .requestMatchers(API + "/admin/**")
                                .hasRole("ADMIN")
                                // Actuator: health, liveness/readiness ve info token'sız açık;
                                // detaylar ve metrikler admin için.
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/actuator/health",
                                        "/actuator/health/*",
                                        "/actuator/info")
                                .permitAll()
                                .requestMatchers("/actuator/**")
                                .hasRole("ADMIN")
                                // OpenAPI ve Swagger UI; prod profilinde istenmedikçe kapalı.
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**")
                                .permitAll()
                                // Varsayılan ret: yeni uç nokta, bir kural açana kadar kapalı.
                                .anyRequest()
                                .denyAll());
        return http.build();
    }

    /** BCrypt rastgele salt kullanır ve bilerek yavaştır (strength 10, yaklaşık 100 ms). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Girişte e-posta ve şifreyi kontrol eder. Bu bean Spring Boot'un varsayılan bellek içi
     * kullanıcısını da engeller.
     */
    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
