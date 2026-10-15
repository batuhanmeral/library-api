package com.batuhanmeral.library.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Her API çağrısı için bir log satırı: metod, yol, durum, süre ve controller metodu, örn. {@code
 * GET /api/v1/books/7 returned 200 in 4 ms (BookController.findById)}.
 *
 * <p>Filter değil interceptor: hangi controller metodunun seçildiğini bilir. Query string
 * log'lanmaz; yol kodlanmış haliyle yazılır, satır sonu içeremez.
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

    /** Cevap tamamlandıktan sonra, controller hata verse de çağrılır; durum artık kesindir. */
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
