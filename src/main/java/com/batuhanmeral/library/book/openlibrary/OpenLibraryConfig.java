package com.batuhanmeral.library.book.openlibrary;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * The RestClient for Open Library, with timeouts of its own.
 *
 * <p>The auto-configured RestClient.Builder already has a request factory (from the global
 * spring.http.clients.* settings) and the customizers applied. Setting another request factory on
 * it afterwards would replace whatever a customizer installed, such as the mock server of
 * {@code @RestClientTest}. This bean is built in the same order as Spring Boot builds its own:
 * request factory first, then every RestClientCustomizer (JSON converters, observations and, in
 * tests, the mock server).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenLibraryProperties.class)
public class OpenLibraryConfig {

    @Bean
    RestClient openLibraryRestClient(
            OpenLibraryProperties properties, ObjectProvider<RestClientCustomizer> customizers) {
        HttpClientSettings settings =
                HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout())
                        // /isbn/{isbn}.json answers with a redirect to the edition.
                        .withRedirects(HttpRedirects.FOLLOW_WHEN_POSSIBLE);
        RestClient.Builder builder =
                RestClient.builder()
                        // The HTTP client on the classpath; here the JDK's
                        // java.net.http.HttpClient.
                        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                        .baseUrl(properties.baseUrl())
                        .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }
}
