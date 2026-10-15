package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Başlık araması {@code lower(title) like '%dune%'}: desen joker ile başladığı için B-tree index
 * kullanılamaz. V4'teki trigram index (pg_trgm) bu sorguya hizmet eder.
 *
 * <p>Az satırda tam tarama ucuzdur; test, sequential scan'i kapatıp index'in kullanılabildiğini
 * gösterir.
 */
@DataJpaTest
class TitleSearchIndexTest implements DatabaseTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void theTitleSearchCanUseTheTrigramIndex() {
        jdbcTemplate.execute("set local enable_seqscan = off");

        // Hibernate'in BookSpecifications.titleContains için yazdığı koşul.
        List<String> plan =
                jdbcTemplate.queryForList(
                        "explain select id from books where lower(title) like ? escape '\\'",
                        String.class,
                        "%dune%");

        assertThat(String.join("\n", plan)).contains("ix_books_title_trgm");
    }
}
