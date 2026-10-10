package com.batuhanmeral.library.common.web;

import com.batuhanmeral.library.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers 400 for a request path that Spring cannot parse, such as {@code /categories/1;x=%zz}: an
 * invalid percent-encoding in a path parameter, which Tomcat passes on without decoding.
 *
 * <p>Without this filter such a path fails in the first component that parses it with an
 * IllegalArgumentException: a 500, Tomcat's HTML error page and several stack traces, because the
 * error page fails on the same path again. Spring Security's firewall rejects every ";" in a path,
 * but Spring Security 7 parses the path before its firewall runs (a ServletRequestPathFilter in
 * front of its chain), so the firewall never sees this one.
 *
 * <p>The answer is written here, as fixed text, and not by GlobalExceptionHandler: Spring MVC also
 * decodes the path while writing a response (to choose a Content-Disposition), and would fail on it
 * once more. Runs right after RequestIdFilter, so the answer still has a request id.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MalformedPathFilter extends OncePerRequestFilter {

    /** The same problem GlobalExceptionHandler writes for the firewall's rejections. */
    static final String BODY =
            """
            {"type":"about:blank","title":"Bad Request","status":400,\
            "detail":"The request was rejected.","errorCode":"%s"}\
            """
                    .formatted(ErrorCode.REQUEST_REJECTED.name());

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // The same parsing Spring MVC and Spring Security do later, without caching the result.
            RequestPath.parse(request.getRequestURI(), request.getContextPath());
        } catch (IllegalArgumentException e) {
            // The path itself is not logged: it is what could not be trusted.
            log.info("Request rejected: the path could not be parsed");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            // JSON is UTF-8 by definition; no charset parameter, like Spring's own responses.
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getOutputStream().write(BODY.getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
