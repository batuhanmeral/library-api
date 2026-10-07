package com.batuhanmeral.library.book.openlibrary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Open Library's publish_date is free text typed by people. The year is the first group of exactly
 * four digits, if it is a plausible publication year.
 */
class PublishedYearTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
            nullValues = "null",
            value = {
                "1987, 1987",
                "'October 1, 1988', 1988",
                "1987-03-01, 1987",
                "c1987, 1987",
                "Spring 2003, 2003",
                "[1965?], 1965",
                "2027, 2027",
                "2028, null",
                "0999, null",
                "12345, null",
                "unknown, null",
                "'', null",
                "null, null"
            })
    void findsAPlausibleYear(String publishDate, Integer expected) {
        assertThat(OpenLibraryClient.yearOf(publishDate, 2027)).isEqualTo(expected);
    }
}
