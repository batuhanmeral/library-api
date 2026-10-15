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
 * Spring'in parse edemediği yol için 400 döner, örn. {@code /categories/1;x=%zz}.
 *
 * <p>Bu filter olmadan 500, Tomcat'in HTML sayfası ve birkaç stack trace çıkar; Spring Security 7
 * yolu firewall'dan önce parse eder. Cevap burada sabit metinle yazılır, Spring MVC cevap yazarken
 * yolu yine parse ederdi.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MalformedPathFilter extends OncePerRequestFilter {

    /** GlobalExceptionHandler'ın firewall retleri için yazdığı problemin aynısı. */
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
            // Spring MVC ve Spring Security'nin sonra yaptığı parse, sonucu cache'lemeden.
            RequestPath.parse(request.getRequestURI(), request.getContextPath());
        } catch (IllegalArgumentException e) {
            // Yol log'lanmaz: güvenilmeyen kısım o.
            log.info("Request rejected: the path could not be parsed");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            // JSON tanım gereği UTF-8; charset parametresi yok.
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getOutputStream().write(BODY.getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
