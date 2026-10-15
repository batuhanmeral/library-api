package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Actuator uçları ve kimin görebileceği: health ve info herkese açık, health detayları ve metrikler
 * admin için.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private UserRepository userRepository;

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() {
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(result).bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void anAdminSeesWhatHealthIsMadeOf() {
        MvcTestResult result =
                mvc.get()
                        .uri("/actuator/health")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .exchange();

        assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.components.db.details.database")
                .isEqualTo("PostgreSQL");
    }

    @Test
    void aMemberSeesOnlyTheStatus() {
        MvcTestResult result =
                mvc.get()
                        .uri("/actuator/health")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, 7))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().doesNotHavePath("$.components");
    }

    /** Container runtime'ın sorduğu gruplar: süreç canlı mı, hizmet verebilir mi. */
    @Test
    void livenessAndReadinessHaveTheirOwnPaths() {
        assertThat(mvc.get().uri("/actuator/health/liveness").exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");

        MvcTestResult readiness =
                mvc.get()
                        .uri("/actuator/health/readiness")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .exchange();
        assertThat(readiness).hasStatus(HttpStatus.OK);
        // Hazır, veritabanı cevap veriyor demek. Redis bilerek dışarıda: cache olmadan da her istek
        // çalışır.
        assertThat(readiness).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
        assertThat(readiness).bodyJson().doesNotHavePath("$.components.redis");
    }

    @Test
    void infoIsPublicAndShowsTheBuild() {
        MvcTestResult result = mvc.get().uri("/actuator/info").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.build.artifact").isEqualTo("library-api");
    }

    @Test
    void metricsAreForAdminsOnly() {
        assertThat(mvc.get().uri("/actuator/metrics").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(
                        mvc.get()
                                .uri("/actuator/metrics")
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, 7))
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(
                        mvc.get()
                                .uri("/actuator/metrics")
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                                .exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.names")
                .asArray()
                .contains("http.server.requests", "jvm.memory.used", "hikaricp.connections")
                .contains("library.loans.borrowed", "library.loans.returned");
    }

    /** env, beans ve diğerleri ayarları ve iç yapıyı gösterir: admin'e bile kapalı. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops",
                "/actuator/heapdump",
                "/actuator/threaddump",
                "/actuator/loggers",
                "/actuator/mappings"
            })
    void endpointsThatAreNotExposedDoNotExist(String path) {
        assertThat(
                        mvc.get()
                                .uri(path)
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                                .exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    /** Health açık, parçaları değil: anonim çağıran adıyla soramaz. */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/db", "/actuator/health/diskSpace"})
    void anAnonymousCallerCannotReadAHealthComponent(String path) {
        assertThat(mvc.get().uri(path).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aCommittedBorrowCountsInTheLoanMetric() {
        double before = borrowedCount();
        Author author = authorRepository.save(new Author("Frank Herbert"));
        Book book = new Book("Dune", 1);
        book.setAuthor(author);
        long bookId = bookRepository.save(book).getId();
        long memberId =
                userRepository
                        .save(new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER))
                        .getId();

        assertThat(borrow(bookId, memberId)).hasStatus(HttpStatus.CREATED);
        // İkinci deneme reddedilir (kopya kalmadı) ve sayılmaz.
        assertThat(borrow(bookId, memberId)).hasStatus(HttpStatus.CONFLICT);

        assertThat(borrowedCount()).isEqualTo(before + 1);
    }

    private MvcTestResult borrow(long bookId, long memberId) {
        return mvc.post()
                .uri("/api/v1/loans")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\": %d}".formatted(bookId))
                .exchange();
    }

    private double borrowedCount() {
        MvcTestResult result =
                mvc.get()
                        .uri("/actuator/metrics/library.loans.borrowed")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        String body =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number count = JsonPath.read(body, "$.measurements[0].value");
        return count.doubleValue();
    }
}
