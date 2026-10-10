package com.batuhanmeral.library.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Who may call what. Business rules are tested elsewhere; here only the answer of the security
 * layer matters: 401 without a valid token, 403 with a token of the wrong role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityRulesTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private JwtProperties jwtProperties;

    @Autowired private Clock clock;

    @Autowired private JdbcTemplate jdbcTemplate;

    private String admin;
    private String member;

    @BeforeEach
    void tokens() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate, "loans", "book_categories", "books", "authors", "categories");
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
        // Rejected before any controller ran, and still with a request id.
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
        // Deny by default: an endpoint someone forgets to add to the rules is closed, not open.
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
        // Until step 9 this path made Spring MVC fail while parsing it: five stack traces and
        // Tomcat's HTML error page. Spring Security 7 parses the path before its firewall, so the
        // firewall does not catch it either; MalformedPathFilter answers before both.
        MvcTestResult result = getWithRawUri("/api/v1/categories/1;x=%zz");

        assertProblem(result, HttpStatus.BAD_REQUEST, "REQUEST_REJECTED");
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
        assertThat(result).bodyText().doesNotContain("%zz");
        assertThat(output).doesNotContain("Unexpected error", "Exception");
    }

    @Test
    void theFirewallRejectsASemicolonInThePathWith400() {
        // A path parameter that parses: StrictHttpFirewall refuses ";" anyway, because servers
        // and frameworks disagree on what it means, which has been used to get around URL rules.
        MvcTestResult result = getWithRawUri("/api/v1/categories/1;jsessionid=abc");

        assertProblem(result, HttpStatus.BAD_REQUEST, "REQUEST_REJECTED");
        assertThat(result).headers().containsHeader(RequestIdFilter.HEADER);
    }

    /** Sets the path as it arrives, undecoded; uri() would encode the "%" itself. */
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
