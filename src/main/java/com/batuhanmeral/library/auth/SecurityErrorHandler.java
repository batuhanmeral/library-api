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
 * Writes the error responses of Spring Security in the same format as every other error.
 *
 * <p>Spring Security rejects requests in filters, before DispatcherServlet, so a
 * {@code @RestControllerAdvice} never sees those exceptions. Spring Security calls these three
 * hooks instead: the entry point for 401 (no or a bad token), the access denied handler for 403 (a
 * token without the needed role), the request rejected handler for requests its firewall refuses
 * (400). Each passes the exception to Spring MVC's exception resolvers, which call
 * GlobalExceptionHandler as if a controller had thrown it.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler, RequestRejectedHandler {

    /** Spring MVC's chain of resolvers, the one DispatcherServlet uses; it includes the advice. */
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
        // No handler: the request never reached a controller.
        if (resolver.resolveException(request, response, null, exception) == null) {
            response.sendError(fallbackStatus);
        }
    }
}
