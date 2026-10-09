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
 * Looks up an edition by ISBN in Open Library: GET /isbn/{isbn}.json, which redirects to the
 * edition record (/books/OL...M.json). An unknown ISBN is a 404.
 *
 * <p>Open Library's data is typed in by people and is not trusted: control and invisible characters
 * are removed, texts are cut to the length of our columns, a year or page count that makes no sense
 * is dropped, and nothing of the response is logged. Other errors are left to the caller as
 * Spring's RestClientException family: HttpServerErrorException (5xx), HttpClientErrorException
 * (4xx other than 404, such as 429 when we ask too often), ResourceAccessException (timeouts,
 * connection failures, a body over 1 MB) and RestClientException (a body that is not the expected
 * JSON, a redirect elsewhere).
 */
@Component
public class OpenLibraryClient {

    /** Column limits of books.title and books.description. */
    static final int MAX_TITLE = 255;

    static final int MAX_DESCRIPTION = 2000;

    /** No real book has more pages than this; a larger number is a typo. */
    static final int MAX_PAGES = 100_000;

    /** Exactly four digits, not part of a longer number: "c1987" -> 1987, "12345" -> none. */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");

    /** The only redirect target we follow: an edition record. */
    private static final Pattern EDITION_PATH = Pattern.compile("/books/OL\\d+M\\.json");

    /** Invisible format characters (direction overrides, zero-width spaces, BOM): removed. */
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");

    /** Control characters and line or paragraph separators other than \\n: a space. */
    private static final Pattern BREAKING = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}&&[^\\n]]");

    /** At most this many lookups wait on Open Library at once; see {@link #findByIsbn}. */
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
     * A GET changes nothing, so trying it again is safe. Only a server error is retried, once and
     * after a short pause: it is often momentary. A timeout is not: the server is slow, and asking
     * again would make the caller wait twice as long and add to the load.
     *
     * <p>{@code @ConcurrencyLimit}: a fifth caller while four lookups are waiting is rejected at
     * once (InvocationRejectedException) instead of tying up another request thread and sending
     * Open Library yet another request. POST /books is open to anyone until sign-in exists.
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
     * One GET. A redirect is returned as its Location, a 404 as an empty reply, other errors are
     * thrown; the error body is not read (it would only end up in a log).
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

    /** The path of a redirect target, if it is an edition on Open Library's own host. */
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
     * The first four-digit group of a free-text date, if it lies between 1000 and {@code
     * latestYear}: "October 1, 1988" -> 1988, "[1965?]" -> 1965, "unknown" -> null.
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

    /** An edition's description is either a string or {"type": "/type/text", "value": "..."}. */
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
     * NFC (one form for letters with accents, as typed text usually has), \\r\\n and \\r as \\n,
     * then: invisible format characters removed, other control characters and separators replaced
     * by a space. A description may keep its line breaks; a title may not. A NUL character would
     * also be refused by PostgreSQL and fail the insert.
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

    /** Cuts to at most maxLength UTF-16 units without leaving half of a surrogate pair. */
    private static String cut(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return text.substring(0, end);
    }

    /**
     * The fields we read from an edition record; Open Library sends many more. JsonNode for the
     * fields whose type varies between records.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Edition(
            String title,
            @JsonProperty("publish_date") String publishDate,
            @JsonProperty("number_of_pages") JsonNode numberOfPages,
            JsonNode description) {}
}
