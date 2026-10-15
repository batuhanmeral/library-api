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
 * Her isteğe bir id verir: MDC'ye (her log satırında görünür) ve X-Request-Id cevap header'ına
 * yazılır.
 *
 * <p>Interceptor değil filter: Spring Security'den önce çalışır, reddedilen istek de id alır.
 * İstemcinin gönderdiği güvenli id korunur. ERROR dispatch'te de çalışır ve ilk geçişteki id'yi
 * kullanır.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    public static final String MDC_KEY = "requestId";

    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    /** Harf, rakam, nokta, alt çizgi ve tire; boşluk ya da satır sonu yok. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = requestIdOf(request);
        request.setAttribute(ATTRIBUTE, id);
        MDC.put(MDC_KEY, id);
        // Zincirden önce: gövde yazıldıktan sonra header eklenemez.
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // Thread başka isteklere de hizmet eder; MDC'de kalan değer onların log'una düşerdi.
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
