package com.batuhanmeral.library.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Sadece web katmanı: controller ve exception handler çalışır, service mock'tur. */
@WebMvcTest(CategoryController.class)
// Security filter'ları dışarıda; yetkiler SecurityRulesTest'te test edilir.
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(OutputCaptureExtension.class)
class CategoryControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private CategoryService categoryService;

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() {
        when(categoryService.findAll())
                .thenThrow(new IllegalStateException("connection to db-internal:5432 refused"));

        MvcTestResult result = mvc.get().uri("/api/v1/categories").exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("db-internal");
    }

    @Test
    void databaseConstraintViolationReturns409WithoutTheDatabaseMessage() {
        // Aynı adlı iki istek service kontrolünü geçebilir; ikinci insert'i unique constraint
        // reddeder.
        when(categoryService.create(any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Unique index or primary key violation: CONSTRAINT_INDEX_6"));

        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Science\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("DATA_CONFLICT");
        assertThat(result).bodyText().doesNotContain("CONSTRAINT_INDEX");
    }

    @Test
    void databaseConstraintViolationLogsTheConstraintNameButNotTheValues(CapturedOutput output) {
        // Veritabanı mesajı reddedilen değeri tekrarlar; satır sonlu değer log'a sahte satır
        // ekleyebilirdi.
        SQLException sqlException =
                new SQLException("duplicate key: VALUES ('Science\nINJECTED fake log line')");
        when(categoryService.create(any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "could not execute statement",
                                new ConstraintViolationException(
                                        "could not execute statement",
                                        sqlException,
                                        "UK_CATEGORIES_NAME")));

        mvc.post()
                .uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Science\"}")
                .exchange();

        assertThat(output).contains("UK_CATEGORIES_NAME").doesNotContain("INJECTED");
    }
}
