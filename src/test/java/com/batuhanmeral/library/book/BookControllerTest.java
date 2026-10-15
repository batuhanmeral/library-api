package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Sadece web katmanı: kitapta kaybedilen yarışın hata cevabına dönüşmesi. */
@WebMvcTest(BookController.class)
// Security filter'ları dışarıda; yetkiler SecurityRulesTest'te test edilir.
@AutoConfigureMockMvc(addFilters = false)
class BookControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private BookService bookService;

    @MockitoBean private BookCreationService bookCreationService;

    @Test
    void anOptimisticLockFailureReturns409ConcurrentUpdate() {
        when(bookService.update(eq(1L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Book.class, 1L));

        MvcTestResult result =
                mvc.put()
                        .uri("/api/v1/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Dune\", \"totalCopies\": 1, \"authorId\": 1}")
                        .exchange();

        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("CONCURRENT_UPDATE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The resource was changed by another request. Reload it and try again.");
        assertThat(result).bodyText().doesNotContain("com.batuhanmeral");
    }
}
