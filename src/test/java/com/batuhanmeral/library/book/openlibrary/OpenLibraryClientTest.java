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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import com.batuhanmeral.library.config.ResilienceConfig;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The client against a fake Open Library. {@code @RestClientTest} starts only what a REST client
 * needs (RestClient support, JSON converters) plus the listed beans, and binds every RestClient
 * built from the auto-configured customizers to a MockRestServiceServer: no request leaves the JVM.
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

        // Jackson reads the number 1988 as the text "1988" for a String field: still a year.
        assertThat(book).isEqualTo(new OpenLibraryBook(null, null, 1988, null));
    }
}
