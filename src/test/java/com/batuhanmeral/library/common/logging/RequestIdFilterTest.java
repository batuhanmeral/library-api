package com.batuhanmeral.library.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Spring olmadan filter: mock istek, mock cevap ve gördüğünü kaydeden bir zincir. */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/books");

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /** İsteğin geri kalanının (controller, service) MDC'de gördüğü. */
    private final AtomicReference<String> idSeenByTheRequest = new AtomicReference<>();

    private final FilterChain chain =
            (req, res) -> idSeenByTheRequest.set(MDC.get(RequestIdFilter.MDC_KEY));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void aRequestWithoutAnIdGetsANewOne() throws Exception {
        filter.doFilter(request, response, chain);

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(idSeenByTheRequest).hasValue(id);
    }

    @Test
    void twoRequestsGetDifferentIds() throws Exception {
        filter.doFilter(request, response, chain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/books"),
                secondResponse,
                new MockFilterChain());

        assertThat(secondResponse.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo(response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void anIdSentByTheClientIsKept() throws Exception {
        request.addHeader(RequestIdFilter.HEADER, "gateway-7f3a.42_b");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("gateway-7f3a.42_b");
        assertThat(idSeenByTheRequest).hasValue("gateway-7f3a.42_b");
    }

    /** Id her log satırına girer: satırı bozamamalı ya da sahte satır üretememeli. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "abc\nERROR forged line",
                "abc def",
                "id ",
                "",
                "0123456789012345678901234567890123456789012345678901234567890123456789"
            })
    void anIdThatIsNotSafeToLogIsReplaced(String sent) throws Exception {
        request.addHeader(RequestIdFilter.HEADER, sent);

        filter.doFilter(request, response, chain);

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotEqualTo(sent).hasSize(36);
        assertThat(idSeenByTheRequest).hasValue(id);
    }

    /**
     * DispatcherServlet'ten hata kaçınca sunucu aynı isteği /error için filter'lardan tekrar
     * geçirir; o satırlar da aynı isteğe aittir.
     */
    @Test
    void anErrorDispatchKeepsTheIdOfTheRequest() throws Exception {
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader(RequestIdFilter.HEADER);

        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/books");
        filter.doFilter(request, response, chain);

        assertThat(idSeenByTheRequest).hasValue(id);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void theIdIsRemovedFromTheMdcWhenTheRequestEnds() throws Exception {
        filter.doFilter(request, response, chain);

        // Thread sunucunun havuzuna döner ve başka isteğe hizmet eder.
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theIdIsRemovedFromTheMdcEvenWhenTheRequestFails() {
        FilterChain failingChain =
                (req, res) -> {
                    throw new ServletException("boom");
                };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
