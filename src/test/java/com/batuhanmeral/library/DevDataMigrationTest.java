package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The dev profile also runs the scripts in db/dev. They must work on every database the migrations
 * run on and must be safe to run again.
 */
@DataJpaTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
class DevDataMigrationTest {

    @Autowired private Flyway flyway;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsTheDemoMemberOnce() {
        flyway.migrate();

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from users where email = 'member@example.com'"
                                        + " and role = 'MEMBER'",
                                Integer.class))
                .isEqualTo(1);
    }
}
