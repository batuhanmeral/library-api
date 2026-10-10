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
 * Who may call what, and how a caller proves who they are.
 *
 * <p>A SecurityFilterChain bean is the current way to configure Spring Security; the old
 * WebSecurityConfigurerAdapter base class was removed in Spring Security 6. Defining this bean
 * turns Spring Boot's default chain off (every endpoint behind a login form with a generated
 * password).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String API = "/api/v1";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, SecurityErrorHandler errors)
            throws Exception {
        http
                // CSRF protection guards against a browser sending its cookies along with a request
                // another site made it send. This API reads the user from the Authorization
                // header, which a browser never adds by itself, and sets no cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // No HTTP session: every request brings its token, nothing is remembered between
                // requests.
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, errors),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                // Rules are checked from top to bottom; the first one that matches decides.
                .authorizeHttpRequests(auth ->
                        auth
                                // Error pages Tomcat forwards to after a request failed;
                                // the original request was already checked.
                                .dispatcherTypeMatchers(DispatcherType.ERROR)
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        API + "/auth/register",
                                        API + "/auth/login")
                                .permitAll()
                                // The catalogue: anyone reads it, admins change it.
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
                                // The owner or an admin; LoanService checks the owner.
                                .requestMatchers(HttpMethod.GET, API + "/loans/*")
                                .authenticated()
                                .requestMatchers(HttpMethod.POST, API + "/loans/*/return")
                                .authenticated()
                                .requestMatchers(API + "/admin/**")
                                .hasRole("ADMIN")
                                // Deny by default: a new endpoint is closed until a rule
                                // above opens it.
                                .anyRequest()
                                .denyAll());
        return http.build();
    }

    /**
     * BCrypt hashes with a random salt and is slow on purpose (strength 10: 2^10 rounds, about 100
     * ms), so a stolen table of hashes cannot be reversed by trying billions of passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Checks an email and password at login: DaoAuthenticationProvider loads the user with the
     * UserDetailsService and compares the password with the hash. Declaring this bean also keeps
     * Spring Boot from creating its default in-memory user.
     */
    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
