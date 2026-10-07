package com.batuhanmeral.library.book.openlibrary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Year;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Looks up an edition by ISBN in Open Library: GET /isbn/{isbn}.json, which redirects to the
 * edition record (/books/OL...M.json). An unknown ISBN is a 404.
 *
 * <p>Open Library's data is typed in by people and is not trusted: texts are cut to the length of
 * our columns, a year or page count that makes no sense is dropped, and nothing of the response is
 * logged. Other errors are left to the caller as Spring's RestClientException family:
 * HttpServerErrorException (5xx), HttpClientErrorException (4xx other than 404, such as 429 when we
 * ask too often), ResourceAccessException (timeouts, connection failures) and RestClientException
 * (a body that is not the expected JSON).
 */
@Component
@RequiredArgsConstructor
public class OpenLibraryClient {

    /** Column limits of books.title and books.description. */
    static final int MAX_TITLE = 255;

    static final int MAX_DESCRIPTION = 2000;

    /** No real book has more pages than this; a larger number is a typo. */
    static final int MAX_PAGES = 100_000;

    /** Exactly four digits, not part of a longer number: "c1987" -> 1987, "12345" -> none. */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");

    private final RestClient openLibraryRestClient;
    private final Clock clock;

    /**
     * A GET changes nothing, so trying it again is safe. Only a server error is retried, once and
     * after a short pause: it is often momentary. A timeout is not: the server is slow, and asking
     * again would make the caller wait twice as long and add to the load.
     */
    @Retryable(includes = HttpServerErrorException.class, maxRetries = 1, delay = 300, jitter = 100)
    public Optional<OpenLibraryBook> findByIsbn(String isbn) {
        try {
            Edition edition =
                    openLibraryRestClient
                            .get()
                            .uri("/isbn/{isbn}.json", isbn)
                            .retrieve()
                            .body(Edition.class);
            return Optional.ofNullable(edition).map(this::toBook);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private OpenLibraryBook toBook(Edition edition) {
        return new OpenLibraryBook(
                cut(blankToNull(edition.title()), MAX_TITLE),
                cut(blankToNull(textOf(edition.description())), MAX_DESCRIPTION),
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

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String cut(String text, int maxLength) {
        return text == null || text.length() <= maxLength ? text : text.substring(0, maxLength);
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
