package com.batuhanmeral.library.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Sadece web katmanı, gerçek service ile oluşturması zor bir hata için. Security filter'ları
 * dışarıda.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private AuthService authService;

    @Test
    void aLoginThatFailsInsideTheServerIs500NotA401() {
        // DaoAuthenticationProvider UserDetailsService'in her hatasını (örn. veritabanı kapalı)
        // bununla sarar; istemcinin hatası değil.
        when(authService.login(any()))
                .thenThrow(
                        new InternalAuthenticationServiceException(
                                "connection to db-internal:5432 refused"));

        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ada@example.com\", \"password\": \"secret!!\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("db-internal");
    }
}
