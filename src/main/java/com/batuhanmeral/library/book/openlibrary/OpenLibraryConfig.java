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
 * Open Library için kendi timeout'ları olan RestClient.
 *
 * <p>Spring Boot'un sırasıyla kurulur: önce request factory, sonra RestClientCustomizer'lar; tersi
 * {@code @RestClientTest}'in mock'unu ezerdi. HTTP istemcisi yönlendirme izlemez; beklenen tek
 * yönlendirmeyi OpenLibraryClient kendisi izler.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenLibraryProperties.class)
public class OpenLibraryConfig {

    /** Bir baskı kaydı birkaç kilobayttır. */
    static final long MAX_RESPONSE_BYTES = 1024 * 1024;

    @Bean
    RestClient openLibraryRestClient(
            OpenLibraryProperties properties, ObjectProvider<RestClientCustomizer> customizers) {
        HttpClientSettings settings =
                HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout())
                        .withRedirects(HttpRedirects.DONT_FOLLOW);
        RestClient.Builder builder =
                RestClient.builder()
                        // Classpath'teki HTTP istemcisi; burada JDK'nın HttpClient'ı.
                        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                        .baseUrl(properties.baseUrl())
                        .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .requestInterceptor(new ResponseSizeLimit(MAX_RESPONSE_BYTES));
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }
}
