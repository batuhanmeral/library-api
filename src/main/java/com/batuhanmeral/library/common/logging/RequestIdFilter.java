package com.batuhanmeral.library.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, so the log lines of one request can be found among those of all
 * others. The id goes into the MDC (Mapped Diagnostic Context), from where the log pattern prints
 * it on every line the request's thread writes (logging.pattern.correlation), and into the
 * X-Request-Id response header, so a client can quote it when reporting a problem.
 *
 * <p>A servlet filter, not an interceptor: it runs for every request that reaches the application,
 * before DispatcherServlet and before the Spring Security filters of step 10, so even a request
 * that is rejected there has an id. The order puts it first among the filters.
 *
 * <p>An id the client sends is kept, so a request can be followed through a gateway or another
 * service that already gave it one. It ends up in every log line of the request, so it must be
 * short and plain; anything else is replaced.
 *
 * <p>When an exception escapes DispatcherServlet, the server sends the same request through the
 * filters a second time, to the error page (an ERROR dispatch). OncePerRequestFilter skips that
 * pass by default; this filter runs in it too and reuses the id from the first pass, kept in a
 * request attribute, so the error's log lines carry the same id as the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    public static final String MDC_KEY = "requestId";

    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    /** Letters, digits, dot, underscore and hyphen: no spaces, line breaks or other characters. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = requestIdOf(request);
        request.setAttribute(ATTRIBUTE, id);
        MDC.put(MDC_KEY, id);
        // Set before the rest of the chain runs: once the body is written, headers can no longer
        // be added.
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // The server reuses this thread for other requests; a value left in the MDC would
            // show up in their log lines.
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static String requestIdOf(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String earlierPass) {
            return earlierPass;
        }
        String sent = request.getHeader(HEADER);
        if (sent != null && SAFE_ID.matcher(sent).matches()) {
            return sent;
        }
        return UUID.randomUUID().toString();
    }
}
