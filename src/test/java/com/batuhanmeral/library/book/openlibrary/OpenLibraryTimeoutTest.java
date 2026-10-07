package com.batuhanmeral.library.book.openlibrary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The read timeout is really applied. A local socket accepts the connection and never answers; the
 * call must give up after the configured time instead of waiting forever. Only the loopback
 * interface is used; nothing leaves the machine.
 */
class OpenLibraryTimeoutTest {

    @Test
    void aServerThatNeverAnswersIsGivenUpOnAfterTheReadTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> acceptAndHold(silent));
            OpenLibraryProperties properties =
                    new OpenLibraryProperties(
                            URI.create("http://127.0.0.1:" + silent.getLocalPort()),
                            Duration.ofSeconds(1),
                            Duration.ofMillis(300),
                            "library-api (test)");
            RestClient restClient =
                    new OpenLibraryConfig()
                            .openLibraryRestClient(
                                    properties,
                                    new StaticListableBeanFactory()
                                            .getBeanProvider(RestClientCustomizer.class));

            // Without a read timeout the call would wait forever (tried: the test hung). The outer
            // limit turns that into a failure.
            long start = System.nanoTime();
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    () ->
                            assertThatThrownBy(() ->
                                            restClient
                                                    .get()
                                                    .uri("/isbn/1.json")
                                                    .retrieve()
                                                    .toBodilessEntity())
                                    .isInstanceOf(ResourceAccessException.class));
            Duration took = Duration.ofNanos(System.nanoTime() - start);

            assertThat(took).isBetween(Duration.ofMillis(250), Duration.ofSeconds(5));
        }
    }

    private static void acceptAndHold(ServerSocket server) {
        try (Socket ignored = server.accept()) {
            Thread.sleep(30_000);
        } catch (Exception e) {
            // The test closed the socket.
        }
    }
}
