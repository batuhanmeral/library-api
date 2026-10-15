package com.batuhanmeral.library.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.common.logging.RequestIdFilter;
import com.batuhanmeral.library.user.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Kim neyi çağırabilir: sadece security katmanının cevabı (geçerli token yoksa 401, yanlış rolde
 * 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityRulesTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private JwtProperties jwtProperties;

    @Autowired private Clock clock;

    private String admin;
    private String member;

    @BeforeEach
    void tokens() {
        admin = TestTokens.admin(jwt);
        member = TestTokens.member(jwt, 77);
    }

    @Test
    void anyoneCanReadTheCatalogue() {
        for (String uri : new String[] {"/api/v1/categories", "/api/v1/authors", "/api/v1/books"}) {
            assertThat(mvc.get().uri(uri)).hasStatus(HttpStatus.OK);
        }
    }

    @Test
    void changingTheCatalogueWithoutATokenIs401WithABearerChallenge() {
        MvcTestResult result = createCategory(null);

        assertProblem(result, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        assertThat(result).headers().hasValue(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        // Hiçbir controller çalışmadan reddedildi, request id yine var.
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/v1/categories");
    }

    @Test
    void aMemberCannotChangeTheCatalogue() {
        assertProblem(createCategory(member), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void anAdminCanChangeTheCatalogue() {
        assertThat(createCategory(admin)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void aBrokenTokenIs401EvenWhereNoTokenIsNeeded() {
        MvcTestResult result =
                mvc.get()
                        .uri("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                        .exchange();

        assertProblem(result, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertThat(result)
                .headers()
                .hasValue(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    }

    @Test
    void anExpiredTokenIs401() {
        JwtService yesterday =
                new JwtService(jwtProperties, Clock.offset(clock, Duration.ofDays(-1)));
        String expired = "Bearer " + yesterday.issue(new AuthenticatedUser(1L, Role.ADMIN)).token();

        assertProblem(createCategory(expired), HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
    }

    @Test
    void aTokenSignedWithAnotherKeyIs401() {
        String otherSecret =
                Base64.getEncoder()
                        .encodeToString(
                                "an-attacker-who-guessed-wrong-32b"
                                        .getBytes(StandardCharsets.UTF_8));
        JwtService forger =
                new JwtService(
                        new JwtProperties(otherSecret, Duration.ofHours(1), jwtProperties.issuer()),
                        clock);

        String forged = "Bearer " + forger.issue(new AuthenticatedUser(1L, Role.ADMIN)).token();

        assertProblem(createCategory(forged), HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
    }

    @Test
    void onlyMembersBorrow() {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/loans")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 1}")
                        .exchange();

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void onlyAdminsListEveryLoanAndUser() {
        for (String uri : new String[] {"/api/v1/loans", "/api/v1/users"}) {
            assertProblem(
                    mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, member).exchange(),
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED");
            assertProblem(
                    mvc.get().uri(uri).exchange(),
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED");
            assertThat(mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, admin))
                    .hasStatus(HttpStatus.OK);
        }
    }

    @Test
    void onlyMembersHaveTheirOwnLoanList() {
        assertThat(mvc.get().uri("/api/v1/loans/me").header(HttpHeaders.AUTHORIZATION, member))
                .hasStatus(HttpStatus.OK);
        assertProblem(
                mvc.get()
                        .uri("/api/v1/loans/me")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .exchange(),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED");
    }

    @Test
    void onlyAdminsStartTheOverdueCheck() {
        assertProblem(
                mvc.post()
                        .uri("/api/v1/admin/jobs/overdue-check")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .exchange(),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED");
    }

    @Test
    void aPathThatNoRuleMentionsIsDeniedToEveryone() {
        // Varsayılan ret: kurallara eklenmeyi unutulan uç nokta kapalıdır.
        assertProblem(
                mvc.get().uri("/api/v1/does-not-exist").exchange(),
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED");
        assertProblem(
                mvc.get()
                        .uri("/api/v1/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .exchange(),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED");
    }

    @Test
    void aPathThatCannotBeParsedIs400InsteadOf500(CapturedOutput output) {
        // MalformedPathFilter olmadan bu yol Spring MVC'yi düşürüyordu; Spring Security 7 yolu
        // firewall'dan önce parse ettiği için firewall da yakalamaz.
        MvcTestResult result = getWithRawUri("/api/v1/categories/1;x=%zz");

        assertProblem(result, HttpStatus.BAD_REQUEST, "REQUEST_REJECTED");
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
        assertThat(result).bodyText().doesNotContain("%zz");
        assertThat(output).doesNotContain("Unexpected error", "Exception");
    }

    @Test
    void theFirewallRejectsASemicolonInThePathWith400() {
        // Parse edilebilen path parametresi: StrictHttpFirewall ";" işaretini yine reddeder, URL
        // kurallarını atlatmak için kullanıldı.
        MvcTestResult result = getWithRawUri("/api/v1/categories/1;jsessionid=abc");

        assertProblem(result, HttpStatus.BAD_REQUEST, "REQUEST_REJECTED");
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
    }

    /** Yolu kodu çözülmeden verir; uri() "%" işaretini kendisi kodlardı. */
    private MvcTestResult getWithRawUri(String rawUri) {
        return mvc.get()
                .uri("/api/v1/categories/1")
                .with(request -> {
                    request.setRequestURI(rawUri);
                    return request;
                })
                .exchange();
    }

    private MvcTestResult createCategory(String authorization) {
        var request =
                mvc.post()
                        .uri("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Science\"}");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }
}
