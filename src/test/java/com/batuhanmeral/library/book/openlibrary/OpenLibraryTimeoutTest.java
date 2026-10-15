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
 * Read timeout gerçekten uygulanıyor mu: yerel soket bağlantıyı kabul eder ve hiç cevap vermez.
 * Sadece loopback kullanılır.
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

            // Read timeout olmasa çağrı sonsuza kadar beklerdi; dış sınır bunu hataya çevirir.
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

    // Soket kullanılmaz, sadece bağlantıyı cevapsız açık tutmak için try içinde.
    @SuppressWarnings("try")
    private static void acceptAndHold(ServerSocket server) {
        try (Socket ignored = server.accept()) {
            Thread.sleep(30_000);
        } catch (Exception e) {
            // Soketi test kapattı.
        }
    }
}
