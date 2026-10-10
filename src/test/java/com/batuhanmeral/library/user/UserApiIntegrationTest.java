package com.batuhanmeral.library.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class UserApiIntegrationTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private UserRepository userRepository;

    private User ada;

    @BeforeEach
    void saveTwoMembers() {
        ada = userRepository.save(new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER));
        userRepository.save(new User("bob@example.com", "{noop}x", "Bob", Role.MEMBER));
    }

    @Test
    void meReturnsTheProfileOfTheTokensUserWithoutThePassword() {
        MvcTestResult result =
                mvc.get()
                        .uri("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, ada.getId()))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(ada.getId().intValue());
        assertThat(result).bodyJson().extractingPath("$.fullName").isEqualTo("Ada");
        assertThat(result).bodyText().doesNotContain("password", "{noop}");
    }

    @Test
    void meNeedsAToken() {
        assertThat(mvc.get().uri("/api/v1/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminsListUsersPageByPage() {
        MvcTestResult result =
                mvc.get()
                        .uri("/api/v1/users?size=1&sort=email,desc")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].email")
                .isEqualTo("bob@example.com");
        assertThat(result).bodyText().doesNotContain("password");
    }

    @Test
    void usersCannotBeSortedByPassword() {
        MvcTestResult result =
                mvc.get()
                        .uri("/api/v1/users?sort=password")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_PARAMETER");
    }
}
