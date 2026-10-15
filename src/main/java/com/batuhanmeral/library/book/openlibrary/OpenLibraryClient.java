package com.batuhanmeral.library.book.openlibrary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Year;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Open Library'de ISBN ile baskı arar: GET /isbn/{isbn}.json bir baskı kaydına yönlendirir,
 * bilinmeyen ISBN 404'tür.
 *
 * <p>Gelen veriye güvenilmez: kontrol karakterleri temizlenir, metinler sütun boyuna kırpılır,
 * anlamsız yıl ve sayfa sayısı atılır, cevaptan hiçbir şey log'lanmaz. Diğer hatalar çağırana
 * RestClientException ailesi olarak gider.
 */
@Component
public class OpenLibraryClient {

    /** books.title ve books.description sütun sınırları. */
    static final int MAX_TITLE = 255;

    static final int MAX_DESCRIPTION = 2000;

    /** Bundan fazla sayfa yazım hatasıdır. */
    static final int MAX_PAGES = 100_000;

    /**
     * Tam dört rakam, daha uzun bir sayının parçası değil: "c1987" 1987 olur, "12345" hiçbir şey.
     */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");

    /** İzlenen tek yönlendirme hedefi: bir baskı kaydı. */
    private static final Pattern EDITION_PATH = Pattern.compile("/books/OL\\d+M\\.json");

    /** Görünmez biçim karakterleri (yön değiştiriciler, sıfır genişlikli boşluk, BOM): silinir. */
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");

    /** \\n dışındaki kontrol karakterleri ve satır/paragraf ayırıcıları: boşluk olur. */
    private static final Pattern BREAKING = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}&&[^\\n]]");

    /** Open Library'yi aynı anda en fazla bu kadar sorgu bekler; bkz. {@link #findByIsbn}. */
    static final int MAX_CONCURRENT_LOOKUPS = 4;

    private final RestClient openLibraryRestClient;
    private final Clock clock;
    private final String host;

    public OpenLibraryClient(
            RestClient openLibraryRestClient, Clock clock, OpenLibraryProperties properties) {
        this.openLibraryRestClient = openLibraryRestClient;
        this.clock = clock;
        this.host = properties.baseUrl().getHost();
    }

    /**
     * Sadece sunucu hatası bir kez, kısa bir aradan sonra yeniden denenir; timeout denenmez, yavaş
     * sunucuya yük bindirir.
     *
     * <p>{@code @ConcurrencyLimit}: dört sorgu beklerken beşinci çağrı hemen reddedilir, bir thread
     * daha bağlanmaz.
     */
    @ConcurrencyLimit(
            limit = MAX_CONCURRENT_LOOKUPS,
            policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
    @Retryable(includes = HttpServerErrorException.class, maxRetries = 1, delay = 300, jitter = 100)
    public Optional<OpenLibraryBook> findByIsbn(String isbn) {
        Reply reply = get("/isbn/{isbn}.json", isbn);
        if (reply.redirect() != null) {
            reply = get(editionPath(reply.redirect()));
            if (reply.redirect() != null) {
                throw new RestClientException("Open Library redirected more than once");
            }
        }
        return Optional.ofNullable(reply.edition()).map(this::toBook);
    }

    /**
     * Tek GET: yönlendirme Location olarak, 404 boş cevap olarak döner, diğer hatalar fırlatılır.
     * Hata gövdesi okunmaz.
     */
    private Reply get(String uriTemplate, Object... variables) {
        return openLibraryRestClient
                .get()
                .uri(uriTemplate, variables)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is3xxRedirection()) {
                        return new Reply(null, response.getHeaders().getLocation());
                    }
                    if (status.value() == 404) {
                        return new Reply(null, null);
                    }
                    if (status.is5xxServerError()) {
                        throw HttpServerErrorException.create(
                                status,
                                response.getStatusText(),
                                response.getHeaders(),
                                null,
                                null);
                    }
                    if (status.isError()) {
                        throw HttpClientErrorException.create(
                                status,
                                response.getStatusText(),
                                response.getHeaders(),
                                null,
                                null);
                    }
                    return new Reply(response.bodyTo(Edition.class), null);
                });
    }

    /** Yönlendirme Open Library'nin kendi host'unda bir baskıysa yolu. */
    private String editionPath(URI location) {
        boolean sameHost = location.getHost() == null || host.equalsIgnoreCase(location.getHost());
        if (!sameHost
                || location.getRawPath() == null
                || !EDITION_PATH.matcher(location.getRawPath()).matches()) {
            throw new RestClientException("Open Library redirected to an unexpected address");
        }
        return location.getRawPath();
    }

    private record Reply(Edition edition, URI redirect) {}

    private OpenLibraryBook toBook(Edition edition) {
        return new OpenLibraryBook(
                cut(blankToNull(clean(edition.title(), false)), MAX_TITLE),
                cut(blankToNull(clean(textOf(edition.description()), true)), MAX_DESCRIPTION),
                yearOf(edition.publishDate(), Year.now(clock).getValue() + 1),
                pagesOf(edition.numberOfPages()));
    }

    /**
     * Serbest metin tarihteki ilk dört rakamlı grup, 1000 ile {@code latestYear} arasındaysa:
     * "October 1, 1988" 1988 olur, "unknown" null.
     */
    static Integer yearOf(String publishDate, int latestYear) {
        if (publishDate == null) {
            return null;
        }
        Matcher matcher = YEAR.matcher(publishDate);
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        return year >= 1000 && year <= latestYear ? year : null;
    }

    private static Integer pagesOf(JsonNode pages) {
        if (pages == null || !pages.isIntegralNumber() || !pages.canConvertToInt()) {
            return null;
        }
        int count = pages.intValue();
        return count >= 1 && count <= MAX_PAGES ? count : null;
    }

    /** Açıklama ya string ya da {"type": "/type/text", "value": "..."} nesnesidir. */
    private static String textOf(JsonNode description) {
        if (description == null) {
            return null;
        }
        if (description.isString()) {
            return description.stringValue();
        }
        JsonNode value = description.path("value");
        return value.isString() ? value.stringValue() : null;
    }

    /**
     * NFC'ye çevirir, satır sonlarını \\n yapar, görünmez karakterleri siler, diğer kontrol
     * karakterlerini boşluk yapar. Başlıkta satır sonu kalmaz; NUL'u PostgreSQL de reddederdi.
     */
    static String clean(String text, boolean keepLineBreaks) {
        if (text == null) {
            return null;
        }
        String result = Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("\\r\\n?", "\n");
        result = INVISIBLE.matcher(result).replaceAll("");
        result = BREAKING.matcher(result).replaceAll(" ");
        return keepLineBreaks ? result : result.replace('\n', ' ');
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** Surrogate çiftini bölmeden en fazla maxLength UTF-16 birimine kırpar. */
    private static String cut(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return text.substring(0, end);
    }

    /** Baskı kaydından okunan alanlar; tipi kayda göre değişenler JsonNode. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Edition(
            String title,
            @JsonProperty("publish_date") String publishDate,
            @JsonProperty("number_of_pages") JsonNode numberOfPages,
            JsonNode description) {}
}
