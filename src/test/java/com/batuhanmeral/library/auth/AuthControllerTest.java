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
 * Web layer only, for a failure that is hard to cause with the real service. The security filters
 * are left out: the login endpoint is public, and this is about the error response.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private AuthService authService;

    @Test
    void aLoginThatFailsInsideTheServerIs500NotA401() {
        // DaoAuthenticationProvider wraps any exception of the UserDetailsService (the database
        // is down, for example) in this AuthenticationException. It is not the client's fault:
        // telling the client to log in would hide the outage.
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
