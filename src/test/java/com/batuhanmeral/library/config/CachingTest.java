package com.batuhanmeral.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.book.BookService;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * What is cached and when an entry is dropped. The tests use Spring's in-memory cache
 * (spring.cache.type=simple) instead of Redis, which they do not have; the annotations, keys and
 * evictions are the same. The other tests run without a cache (spring.cache.type=none), so they
 * never see an entry left behind by another test.
 *
 * <p>Hibernate's statistics count the SQL statements: a request answered from the cache runs none.
 */
@SpringBootTest(
        properties = {
            "spring.cache.type=simple",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
@AutoConfigureMockMvc
class CachingTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private CacheManager cacheManager;

    @Autowired private UserRepository userRepository;

    @Autowired private EntityManagerFactory entityManagerFactory;

    @Autowired private BookService bookService;

    private Statistics statistics;

    private long authorId;

    @BeforeEach
    void startEmpty() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        authorId = idOf(asAdmin(post("/api/v1/authors", "{\"name\": \"Frank Herbert\"}")));
    }

    @Test
    void aBookIsReadFromTheDatabaseOnceAndThenFromTheCache() {
        long bookId = createBook("Dune", null);

        statistics.clear();
        MvcTestResult first = get("/api/v1/books/" + bookId);
        assertThat(statistics.getPrepareStatementCount()).isPositive();

        statistics.clear();
        MvcTestResult second = get("/api/v1/books/" + bookId);
        assertThat(statistics.getPrepareStatementCount()).isZero();
        assertThat(body(second)).isEqualTo(body(first));
    }

    @Test
    void anUnknownBookIsNotCached() {
        assertThat(get("/api/v1/books/999999")).hasStatus(HttpStatus.NOT_FOUND);

        statistics.clear();
        assertThat(get("/api/v1/books/999999")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(statistics.getPrepareStatementCount()).isPositive();
    }

    @Test
    void changingOrDeletingABookDropsItsEntry() {
        long bookId = createBook("Dune", null);
        get("/api/v1/books/" + bookId);

        asAdmin(patch("/api/v1/books/" + bookId, "{\"title\": \"Dune (1965)\"}"));
        assertThat(titleOf(get("/api/v1/books/" + bookId))).isEqualTo("Dune (1965)");

        asAdmin(mvc.delete().uri("/api/v1/books/" + bookId));
        assertThat(get("/api/v1/books/" + bookId)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void borrowingAndReturningDropTheBookSoItsAvailableCopiesAreRight() {
        long bookId = createBook("Dune", null);
        long memberId =
                userRepository
                        .save(new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER))
                        .getId();
        String member = TestTokens.member(jwt, memberId);
        assertThat(availableCopies(get("/api/v1/books/" + bookId))).isEqualTo(1);

        MvcTestResult loan =
                mvc.post()
                        .uri("/api/v1/loans")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + bookId + "}")
                        .exchange();
        assertThat(loan).hasStatus(HttpStatus.CREATED);
        assertThat(availableCopies(get("/api/v1/books/" + bookId))).isZero();

        mvc.post()
                .uri("/api/v1/loans/" + idOf(loan) + "/return")
                .header(HttpHeaders.AUTHORIZATION, member)
                .exchange();
        assertThat(availableCopies(get("/api/v1/books/" + bookId))).isEqualTo(1);
    }

    @Test
    void renamingAnAuthorOrACategoryDropsTheBooksThatShowTheName() {
        long categoryId = idOf(asAdmin(post("/api/v1/categories", "{\"name\": \"SF\"}")));
        long bookId = createBook("Dune", categoryId);
        get("/api/v1/books/" + bookId);

        asAdmin(put("/api/v1/categories/" + categoryId, "{\"name\": \"Science fiction\"}"));
        asAdmin(put("/api/v1/authors/" + authorId, "{\"name\": \"F. Herbert\"}"));

        MvcTestResult book = get("/api/v1/books/" + bookId);
        assertThat(JsonPath.<String>read(body(book), "$.categories[0].name"))
                .isEqualTo("Science fiction");
        assertThat(JsonPath.<String>read(body(book), "$.author.name")).isEqualTo("F. Herbert");
    }

    @Test
    void theCategoryListIsCachedAndEveryChangeOfACategoryDropsIt() {
        long science = idOf(asAdmin(post("/api/v1/categories", "{\"name\": \"Science\"}")));
        get("/api/v1/categories");

        statistics.clear();
        assertThat(names()).containsExactly("Science");
        assertThat(statistics.getPrepareStatementCount()).isZero();

        long poetry = idOf(asAdmin(post("/api/v1/categories", "{\"name\": \"Poetry\"}")));
        assertThat(names()).containsExactlyInAnyOrder("Science", "Poetry");

        asAdmin(put("/api/v1/categories/" + poetry, "{\"name\": \"Verse\"}"));
        assertThat(names()).containsExactlyInAnyOrder("Science", "Verse");

        asAdmin(mvc.delete().uri("/api/v1/categories/" + science));
        assertThat(names()).containsExactly("Verse");
    }

    @Test
    void aRefusedChangeKeepsTheEntry() {
        long bookId = createBook("Dune", null);
        get("/api/v1/books/" + bookId);

        MvcTestResult refused = asAdmin(patch("/api/v1/books/" + bookId, "{\"totalCopies\": -1}"));
        assertThat(refused).hasStatus(HttpStatus.BAD_REQUEST);

        statistics.clear();
        get("/api/v1/books/" + bookId);
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    /**
     * The cache sits outside the transaction: a hit returns before a transaction (and a database
     * connection) is taken, and an entry is dropped only after the change is committed. Otherwise a
     * request in between could read the old row and put it back into the cache.
     */
    @Test
    void theCacheWrapsTheTransaction() {
        assertThat(adviceOf(bookService))
                .containsSubsequence("CacheInterceptor", "TransactionInterceptor");
    }

    private long createBook(String title, Long categoryId) {
        String categories = categoryId == null ? "" : ", \"categoryIds\": [" + categoryId + "]";
        return idOf(
                asAdmin(
                        post(
                                "/api/v1/books",
                                "{\"title\": \"%s\", \"totalCopies\": 1, \"authorId\": %d%s}"
                                        .formatted(title, authorId, categories))));
    }

    private List<String> names() {
        return JsonPath.read(body(get("/api/v1/categories")), "$[*].name");
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcTester.MockMvcRequestBuilder put(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcTester.MockMvcRequestBuilder patch(String uri, String json) {
        return mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MvcTestResult asAdmin(MockMvcTester.MockMvcRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt)).exchange();
    }

    private static List<String> adviceOf(Object bean) {
        return Arrays.stream(((Advised) bean).getAdvisors())
                .map(Advisor::getAdvice)
                .map(CachingTest::nameOf)
                .toList();
    }

    private static String nameOf(Advice advice) {
        return advice instanceof AbstractAspectJAdvice aspectAdvice
                ? aspectAdvice.getAspectName()
                : advice.getClass().getSimpleName();
    }

    private static String titleOf(MvcTestResult result) {
        return JsonPath.read(body(result), "$.title");
    }

    private static int availableCopies(MvcTestResult result) {
        return JsonPath.read(body(result), "$.availableCopies");
    }

    private static long idOf(MvcTestResult result) {
        Number id = JsonPath.read(body(result), "$.id");
        return id.longValue();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
