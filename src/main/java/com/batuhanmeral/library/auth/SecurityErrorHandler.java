package com.batuhanmeral.library.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security'nin filter'larda verdiği 401, 403 ve 400 hatalarını Spring MVC'nin
 * resolver'larına gönderir, böylece GlobalExceptionHandler aynı formatta cevap yazar.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler, RequestRejectedHandler {

    /** DispatcherServlet'in kullandığı resolver zinciri; advice'ı da içerir. */
    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver resolver;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        resolve(request, response, authException, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        resolve(request, response, accessDeniedException, HttpServletResponse.SC_FORBIDDEN);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            RequestRejectedException requestRejectedException)
            throws IOException {
        resolve(request, response, requestRejectedException, HttpServletResponse.SC_BAD_REQUEST);
    }

    private void resolve(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception exception,
            int fallbackStatus)
            throws IOException {
        // Handler yok: istek bir controller'a ulaşmadı.
        if (resolver.resolveException(request, response, null, exception) == null) {
            response.sendError(fallbackStatus);
        }
    }
}
