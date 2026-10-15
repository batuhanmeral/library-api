package com.batuhanmeral.library.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * POST /auth/login ve /auth/register sınırları, gerçek Redis ile. Sınırlar küçük tutuldu, birkaç
 * istek yeter.
 */
@SpringBootTest(
        properties = {
            "library.security.rate-limit.enabled=true",
            "library.security.rate-limit.login.limit=3",
            "library.security.rate-limit.login.window=1m",
            "library.security.rate-limit.register.limit=2",
            "library.security.rate-limit.register.window=1h"
        })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RateLimitTest implements DatabaseTest {

    /** redis:8.10.3-alpine, docker-compose.yml'deki gibi digest ile sabit. */
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                            DockerImageName.parse(
                                    "redis@sha256:7958cfbb9d152f87dadfbc89f1b0b15938f0b81ff8982feb327476f4911935c3"))
                    .withExposedPorts(6379);

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CLIENT = "198.51.100.7";

    @Autowired private MockMvcTester mvc;

    @Autowired private StringRedisTemplate redis;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void emptyRedisAndSaveAMember() {
        redis.execute(
                connection -> {
                    connection.serverCommands().flushAll();
                    return null;
                },
                true);
        userRepository.save(
                new User(
                        "ada@example.com",
                        passwordEncoder.encode("correct horse"),
                        "Ada Lovelace",
                        Role.MEMBER));
    }

    @Test
    void theFourthLoginInAMinuteIsRefusedWithTheTimeToWait() {
        assertThat(login(CLIENT, "correct horse")).hasStatus(HttpStatus.OK);
        assertThat(login(CLIENT, "wrong password")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(CLIENT, "correct horse")).hasStatus(HttpStatus.OK);

        MvcTestResult refused = login(CLIENT, "correct horse");

        assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refused).bodyJson().extractingPath("$.errorCode").isEqualTo("TOO_MANY_REQUESTS");
        assertThat(refused).bodyJson().extractingPath("$.instance").isEqualTo(LOGIN);
        String retryAfter = refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    /** Sınıra ulaşınca şifre hiç kontrol edilmez, BCrypt çalışmaz. */
    @Test
    void aRefusedLoginDoesNotTellWhetherThePasswordWasRight() {
        for (int i = 0; i < 3; i++) {
            login(CLIENT, "wrong password");
        }

        assertThat(login(CLIENT, "correct horse")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login(CLIENT, "wrong password")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void eachClientAddressHasItsOwnCounter() {
        for (int i = 0; i < 3; i++) {
            login(CLIENT, "correct horse");
        }
        assertThat(login(CLIENT, "correct horse")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(login("198.51.100.8", "correct horse")).hasStatus(HttpStatus.OK);
    }

    /**
     * Bir IPv6 müşterisi genelde bütün bir /64 alır; adres başına sayılsa her denemede yeni adres
     * kullanabilirdi.
     */
    @Test
    void ipv6AddressesOfOneNetworkShareACounter() {
        login("2001:db8:1:2::1", "correct horse");
        login("2001:db8:1:2::2", "correct horse");
        login("2001:db8:1:2:ffff:ffff:ffff:ffff", "correct horse");

        assertThat(login("2001:db8:1:2::abcd", "correct horse"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login("2001:db8:1:3::1", "correct horse")).hasStatus(HttpStatus.OK);
    }

    @Test
    void registrationHasALimitOfItsOwn() {
        assertThat(register(CLIENT, "grace@example.com")).hasStatus(HttpStatus.CREATED);
        assertThat(register(CLIENT, "grace@example.com")).hasStatus(HttpStatus.CONFLICT);

        assertThat(register(CLIENT, "linus@example.com")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(userRepository.findByEmail("linus@example.com")).isEmpty();
        assertThat(login(CLIENT, "correct horse")).hasStatus(HttpStatus.OK);
    }

    /** Pencere bitince Redis sayacı kendisi siler. */
    @Test
    void theCountersLiveInRedisUnderTheApplicationsPrefixAndExpire() {
        login(CLIENT, "correct horse");
        register(CLIENT, "grace@example.com");

        String loginKey = "library-api:rate-limit:login:" + CLIENT;
        String registerKey = "library-api:rate-limit:register:" + CLIENT;
        Set<String> keys = redis.keys("*");
        assertThat(keys).containsExactlyInAnyOrder(loginKey, registerKey);
        assertThat(redis.opsForValue().get(loginKey)).isEqualTo("1");
        assertThat(redis.getExpire(loginKey)).isBetween(1L, Duration.ofMinutes(1).toSeconds());
        assertThat(redis.getExpire(registerKey)).isBetween(61L, Duration.ofHours(1).toSeconds());
    }

    /** Redis yoksa sınır sayılamaz ama giriş çalışır (500 ms bekledikten sonra); log bunu yazar. */
    @Test
    void aRedisThatStopsAnsweringLetsLoginsThrough(CapturedOutput output) {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            for (int i = 0; i < 4; i++) {
                assertThat(login(CLIENT, "correct horse")).hasStatus(HttpStatus.OK);
            }
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        assertThat(output).contains("Rate limit for login not checked");
    }

    private MvcTestResult login(String clientAddress, String password) {
        return mvc.post()
                .uri(LOGIN)
                .with(request -> {
                    request.setRemoteAddr(clientAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"email": "ada@example.com", "password": "%s"}
                        """
                                .formatted(password))
                .exchange();
    }

    private MvcTestResult register(String clientAddress, String email) {
        return mvc.post()
                .uri(REGISTER)
                .with(request -> {
                    request.setRemoteAddr(clientAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"email": "%s", "password": "correct horse", "fullName": "Grace Hopper"}
                        """
                                .formatted(email))
                .exchange();
    }
}
