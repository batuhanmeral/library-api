package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The title search ({@code ?title=dune}) is {@code lower(title) like '%dune%'}: the pattern starts
 * with a wildcard, so an ordinary B-tree index cannot be used and every row is read. The trigram
 * index of V4 (pg_trgm) splits the titles into three-letter pieces and finds the rows that contain
 * all pieces of the pattern.
 *
 * <p>With a few rows a full scan is cheaper and PostgreSQL rightly picks it; turning sequential
 * scans off for the test's transaction shows whether the index can serve the query at all.
 */
@DataJpaTest
class TitleSearchIndexTest implements DatabaseTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void theTitleSearchCanUseTheTrigramIndex() {
        jdbcTemplate.execute("set local enable_seqscan = off");

        // The condition as Hibernate writes it for BookSpecifications.titleContains.
        List<String> plan =
                jdbcTemplate.queryForList(
                        "explain select id from books where lower(title) like ? escape '\\'",
                        String.class,
                        "%dune%");

        assertThat(String.join("\n", plan)).contains("ix_books_title_trgm");
    }
}
