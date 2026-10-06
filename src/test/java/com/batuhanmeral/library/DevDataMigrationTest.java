package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    void theDemoMemberHasAFictionalPhoneForSmsReminders() {
        // 555-0100 to 555-0199 are reserved for fiction in the North American numbering plan.
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select phone from users where email = 'member@example.com'",
                                String.class))
                .isEqualTo("+15555550100");
    }

    /**
     * A database migrated before the phone was added: the script is changed, so Flyway runs it
     * again (here forced by forgetting that it ran). It must add the phone, not a second member.
     * Flyway works on its own connection, so this test must not hold the rows in an open test
     * transaction.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aDemoMemberFromAnEarlierVersionGetsThePhoneToo() {
        jdbcTemplate.update("update users set phone = null where email = 'member@example.com'");
        // Flyway quotes its table and column names; H2 would read them upper case otherwise.
        jdbcTemplate.update("delete from \"flyway_schema_history\" where \"version\" is null");

        flyway.migrate();

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select phone from users where email = 'member@example.com'",
                                String.class))
                .isEqualTo("+15555550100");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from users where email = 'member@example.com'",
                                Integer.class))
                .isEqualTo(1);
    }
}
