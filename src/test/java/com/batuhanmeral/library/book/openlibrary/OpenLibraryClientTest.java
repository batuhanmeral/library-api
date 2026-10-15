package com.batuhanmeral.library.book.openlibrary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import com.batuhanmeral.library.config.ResilienceConfig;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Sahte bir Open Library'ye karşı istemci. {@code @RestClientTest} her RestClient'ı
 * MockRestServiceServer'a bağlar, istek JVM'den çıkmaz.
 */
@RestClientTest(
        components = OpenLibraryClient.class,
        properties = {
            "library.open-library.base-url=https://openlibrary.test",
            "library.open-library.user-agent=library-api (test)"
        })
@Import({OpenLibraryConfig.class, ResilienceConfig.class, OpenLibraryClientTest.FixedClock.class})
class OpenLibraryClientTest {

    private static final String DUNE_URL = "https://openlibrary.test/isbn/9780441172719.json";
    private static final String EDITION_URL = "https://openlibrary.test/books/OL22597282M.json";

    @Autowired private OpenLibraryClient client;

    @Autowired private MockRestServiceServer server;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void readsTheEditionOfTheIsbn() {
        server.expect(once(), requestTo(DUNE_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "library-api (test)"))
                .andRespond(
                        withSuccess(
                                """
                                {"title": "Dune", "publish_date": "October 1, 1988",
                                 "number_of_pages": 535, "description": "A desert planet.",
                                 "key": "/books/OL22597282M", "covers": [1]}
                                """,
                                MediaType.APPLICATION_JSON));

        OpenLibraryBook book = client.findByIsbn("9780441172719").orElseThrow();

        assertThat(book).isEqualTo(new OpenLibraryBook("Dune", "A desert planet.", 1988, 535));
        server.verify();
    }

    @Test
    void aDescriptionCanBeATextObject() {
        server.expect(requestTo(DUNE_URL))
                .andRespond(
                        withSuccess(
                                """
                                {"title": "Dune",
                                 "description": {"type": "/type/text", "value": "A desert planet."}}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(client.findByIsbn("9780441172719").orElseThrow().description())
                .isEqualTo("A desert planet.");
    }

    @Test
    void anUnknownIsbnIsEmpty() {
        server.expect(requestTo(DUNE_URL)).andRespond(withResourceNotFound());

        assertThat(client.findByIsbn("9780441172719")).isEmpty();
    }

    @Test
    void aServerErrorIsRetriedOnce() {
        server.expect(once(), requestTo(DUNE_URL)).andRespond(withServiceUnavailable());
        server.expect(once(), requestTo(DUNE_URL))
                .andRespond(withSuccess("{\"title\": \"Dune\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findByIsbn("9780441172719")).isPresent();
        server.verify();
    }

    @Test
    void aServerErrorThatPersistsReachesTheCaller() {
        server.expect(times(2), requestTo(DUNE_URL)).andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> client.findByIsbn("9780441172719"))
                .isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    @Test
    void aTimeoutIsNotRetried() {
        server.expect(once(), requestTo(DUNE_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.findByIsbn("9780441172719"))
                .isInstanceOf(ResourceAccessException.class);
        server.verify();
    }

    @Test
    void tooManyRequestsIsAnErrorAndNotRetried() {
        server.expect(once(), requestTo(DUNE_URL)).andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.findByIsbn("9780441172719"))
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }

    @Test
    void valuesThatDoNotFitAreCutOrDropped() {
        String longTitle = "T".repeat(300);
        String longDescription = "D".repeat(2500);
        server.expect(requestTo(DUNE_URL))
                .andRespond(
                        withSuccess(
                                """
                                {"title": "%s", "description": "%s",
                                 "publish_date": "2999", "number_of_pages": -5}
                                """
                                        .formatted(longTitle, longDescription),
                                MediaType.APPLICATION_JSON));

        OpenLibraryBook book = client.findByIsbn("9780441172719").orElseThrow();

        assertThat(book.title()).hasSize(255);
        assertThat(book.description()).hasSize(2000);
        assertThat(book.publishedYear()).isNull();
        assertThat(book.pageCount()).isNull();
    }

    @Test
    void valuesOfTheWrongTypeAreDropped() {
        server.expect(requestTo(DUNE_URL))
                .andRespond(
                        withSuccess(
                                """
                                {"title": "   ", "number_of_pages": "many",
                                 "description": {"type": "/type/text"}, "publish_date": 1988}
                                """,
                                MediaType.APPLICATION_JSON));

        OpenLibraryBook book = client.findByIsbn("9780441172719").orElseThrow();

        // Jackson 1988 sayısını String alan için "1988" okur: yine bir yıl.
        assertThat(book).isEqualTo(new OpenLibraryBook(null, null, 1988, null));
    }

    @Test
    void theRedirectToTheEditionIsFollowed() {
        server.expect(once(), requestTo(DUNE_URL))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(EDITION_URL)));
        server.expect(once(), requestTo(EDITION_URL))
                .andRespond(withSuccess("{\"title\": \"Dune\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findByIsbn("9780441172719").orElseThrow().title()).isEqualTo("Dune");
        server.verify();
    }

    @Test
    void aRelativeRedirectIsFollowedToo() {
        server.expect(once(), requestTo(DUNE_URL))
                .andRespond(
                        withStatus(HttpStatus.FOUND)
                                .location(URI.create("/books/OL22597282M.json")));
        server.expect(once(), requestTo(EDITION_URL))
                .andRespond(withSuccess("{\"title\": \"Dune\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findByIsbn("9780441172719")).isPresent();
        server.verify();
    }

    @Test
    void aRedirectElsewhereIsNotFollowed() {
        server.expect(once(), requestTo(DUNE_URL))
                .andRespond(
                        withStatus(HttpStatus.FOUND)
                                .location(URI.create("https://internal.example/admin.json")));

        assertThatThrownBy(() -> client.findByIsbn("9780441172719"))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void controlAndInvisibleCharactersAreRemoved() {
        server.expect(requestTo(DUNE_URL))
                .andRespond(
                        withSuccess(
                                """
                                {"title": "Du\\u202Ene\\u0000 Messiah",
                                 "description": "Line one\\r\\nLine two\\u0007\\u2028end"}
                                """,
                                MediaType.APPLICATION_JSON));

        OpenLibraryBook book = client.findByIsbn("9780441172719").orElseThrow();

        assertThat(book.title()).isEqualTo("Dune  Messiah");
        assertThat(book.description()).isEqualTo("Line one\nLine two  end");
    }

    @Test
    void cuttingATitleDoesNotSplitACharacter() {
        // 254 harf ve iki UTF-16 birimlik bir emoji: toplam 256 birim.
        String title = "a".repeat(254) + "\uD83D\uDCD6";
        server.expect(requestTo(DUNE_URL))
                .andRespond(
                        withSuccess(
                                "{\"title\": \"%s\"}".formatted(title),
                                MediaType.APPLICATION_JSON));

        String cut = client.findByIsbn("9780441172719").orElseThrow().title();

        assertThat(cut).isEqualTo("a".repeat(254));
    }

    @Test
    void aResponseLargerThanOneMegabyteIsRefused() {
        String huge = "{\"title\": \"Dune\", \"notes\": \"%s\"}".formatted("x".repeat(1_100_000));
        server.expect(requestTo(DUNE_URL))
                .andRespond(withSuccess(huge, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findByIsbn("9780441172719"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void atMostFourLookupsRunAtOnceAndTheFifthIsRejected() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger arrived = new AtomicInteger();
        server.expect(ExpectedCount.manyTimes(), requestTo(DUNE_URL))
                .andRespond(request -> {
                    arrived.incrementAndGet();
                    await(release);
                    return withResourceNotFound().createResponse(request);
                });
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> running = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                running.add(executor.submit(() -> client.findByIsbn("9780441172719")));
            }
            waitUntil(arrived, 4);

            assertThatThrownBy(() -> client.findByIsbn("9780441172719"))
                    .isInstanceOf(InvocationRejectedException.class);

            release.countDown();
            for (Future<?> call : running) {
                call.get(10, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** Verilen sayıda çağrı sahte sunucuya ulaşıp bekleyene kadar yoklar. */
    private static void waitUntil(AtomicInteger arrived, int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (arrived.get() < count) {
            assertThat(System.nanoTime()).as("lookups did not start").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
