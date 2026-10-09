package com.batuhanmeral.library.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Writes one log line per API call: method, path, status, duration and the controller method that
 * handled it, for example {@code GET /api/v1/books/7 returned 200 in 4 ms
 * (BookController.findById)}. The request id comes from the MDC (RequestIdFilter).
 *
 * <p>An interceptor, not a filter: it runs inside DispatcherServlet, after the handler has been
 * chosen, so it knows which controller method serves the request. A filter only sees the servlet
 * request. The other side of it: a request for which no handler is found (405 for a method the path
 * does not support) never reaches an interceptor.
 *
 * <p>The query string is not logged: it may hold search terms or other data that does not belong in
 * a log. The path is logged as the server received it, still percent-encoded, so it cannot hold a
 * raw line break.
 */
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START = RequestLoggingInterceptor.class.getName() + ".start";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    /**
     * Called after the response is complete, also when the controller threw: by then the exception
     * handler (GlobalExceptionHandler) has set the status the client gets.
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        if (!(request.getAttribute(START) instanceof Long start)) {
            return;
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        log.info(
                "{} {} returned {} in {} ms ({})",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                millis,
                describe(handler));
    }

    private static String describe(Object handler) {
        if (handler instanceof HandlerMethod method) {
            return method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
        }
        return handler.getClass().getSimpleName();
    }
}
