package com.batuhanmeral.library.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest implements DatabaseTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired private MockMvcTester mvc;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void registerCreatesAMemberAndStoresOnlyABcryptHash() {
        MvcTestResult result =
                register(
                        """
                        {"email": "Ada@Example.com", "password": "correct horse",
                         "fullName": "Ada Lovelace", "phone": "+15555550142"}
                        """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("ada@example.com");
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("MEMBER");
        assertThat(result).bodyJson().extractingPath("$.phone").isEqualTo("+15555550142");
        assertThat(result).bodyText().doesNotContain("password", "correct horse");
        User saved = userRepository.findByEmail("ada@example.com").orElseThrow();
        assertThat(saved.getPassword()).startsWith("$2a$").isNotEqualTo("correct horse");
        assertThat(passwordEncoder.matches("correct horse", saved.getPassword())).isTrue();
    }

    @Test
    void registerIgnoresARoleSentByTheClient() {
        MvcTestResult result =
                register(
                        """
                        {"email": "eve@example.com", "password": "correct horse",
                         "fullName": "Eve", "role": "ADMIN"}
                        """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("MEMBER");
    }

    @Test
    void registerRejectsAnEmailThatIsTakenInAnyCase() {
        register(member("ada@example.com"));

        MvcTestResult result = register(member("ADA@example.com"));

        assertProblem(result, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Too short.
                "{\"email\": \"a@example.com\", \"password\": \"short\", \"fullName\": \"A\"}",
                // 37 characters but 74 bytes in UTF-8: BCrypt reads only the first 72 bytes.
                "{\"email\": \"a@example.com\", \"password\": \"ğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğ\","
                        + " \"fullName\": \"A\"}",
                // Not an email address.
                "{\"email\": \"not-an-email\", \"password\": \"correct horse\", \"fullName\": \"A\"}",
                // Not a phone number in international form.
                "{\"email\": \"a@example.com\", \"password\": \"correct horse\", \"fullName\": \"A\","
                        + " \"phone\": \"call me\"}",
                // No name.
                "{\"email\": \"a@example.com\", \"password\": \"correct horse\"}"
            })
    void registerValidatesTheRequest(String json) {
        assertProblem(register(json), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void loginReturnsABearerTokenThatOpensTheUsersOwnProfile() {
        register(member("ada@example.com"));

        MvcTestResult result = login("ADA@example.com", "correct horse");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(3600);
        String token = JsonPath.read(body(result), "$.accessToken");
        assertThat(
                        mvc.get()
                                .uri("/api/v1/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("ada@example.com");
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() {
        register(member("ada@example.com"));

        MvcTestResult wrongPassword = login("ada@example.com", "wrong password");
        MvcTestResult unknownEmail = login("nobody@example.com", "correct horse");

        assertProblem(wrongPassword, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertProblem(unknownEmail, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        // Nothing tells the caller whether the address has an account.
        assertThat(body(unknownEmail)).isEqualTo(body(wrongPassword));
        assertThat(wrongPassword).headers().hasValue(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    void anAccountWithoutAUsablePasswordCannotLogIn() {
        // Like the demo member of the dev profile: "!" is not a BCrypt hash.
        userRepository.save(new User("demo@example.com", "!", "Demo", Role.MEMBER));

        assertProblem(
                login("demo@example.com", "!"), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    @Test
    void aLoginPasswordOver72BytesIsRejectedLikeAtRegistration() {
        // 37 characters, 74 bytes: the same limit as registration, in bytes.
        assertProblem(
                login("ada@example.com", "ğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğğ"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
    }

    @Test
    void loginValidatesTheRequest() {
        assertProblem(
                mvc.post()
                        .uri(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ada@example.com\"}")
                        .exchange(),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
    }

    private MvcTestResult register(String json) {
        return mvc.post()
                .uri(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post()
                .uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))
                .exchange();
    }

    private static String member(String email) {
        return "{\"email\": \"%s\", \"password\": \"correct horse\", \"fullName\": \"Ada\"}"
                .formatted(email);
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
