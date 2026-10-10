package com.batuhanmeral.library.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The filter and the interceptor inside the running application: MockMvc passes requests through
 * the same filters and the same DispatcherServlet as the server would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingTest {

    private static final String CATEGORIES = "/api/v1/categories";

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Test
    void everyResponseHasARequestId() {
        MvcTestResult result = mvc.get().uri(CATEGORIES).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
    }

    @Test
    void anErrorResponseHasARequestIdToo() {
        MvcTestResult result = mvc.get().uri(CATEGORIES + "/999999").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
    }

    @Test
    void eachApiCallIsLoggedWithItsRequestIdStatusAndHandler(CapturedOutput output) {
        mvc.get().uri(CATEGORIES).header(RequestIdFilter.HEADER, "logging-test-1").exchange();

        assertThat(linesWith(output, "logging-test-1"))
                .singleElement()
                .asString()
                .contains("[logging-test-1]")
                .contains("GET /api/v1/categories returned 200 in ")
                .contains(" ms (CategoryController.findAll)");
    }

    @Test
    void anErrorIsLoggedWithItsStatus(CapturedOutput output) {
        mvc.get()
                .uri(CATEGORIES + "/999999")
                .header(RequestIdFilter.HEADER, "logging-test-2")
                .exchange();

        assertThat(linesWith(output, "logging-test-2"))
                .anyMatch(line -> line.contains("GET /api/v1/categories/999999 returned 404 in "));
    }

    @Test
    void theQueryStringIsNotLogged(CapturedOutput output) {
        mvc.get()
                .uri("/api/v1/books?title=secret")
                .header(RequestIdFilter.HEADER, "logging-test-3")
                .exchange();

        assertThat(linesWith(output, "logging-test-3"))
                .anyMatch(line -> line.contains("GET /api/v1/books returned 200"))
                .noneMatch(line -> line.contains("secret"));
    }

    /**
     * The difference between the two: the filter runs for every request, the interceptor only once
     * DispatcherServlet has found a handler. DELETE on the collection has no handler (405), so the
     * response still gets a request id but the interceptor writes no line.
     */
    @Test
    void aRequestWithoutAHandlerGetsAnIdButNoInterceptorLine(CapturedOutput output) {
        MvcTestResult result =
                mvc.delete()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(CATEGORIES)
                        .header(RequestIdFilter.HEADER, "logging-test-4")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).headers().hasValue(RequestIdFilter.HEADER, "logging-test-4");
        assertThat(output).doesNotContain("returned 405");
    }

    private static List<String> linesWith(CapturedOutput output, String text) {
        return output.getOut().lines().filter(line -> line.contains(text)).toList();
    }
}
