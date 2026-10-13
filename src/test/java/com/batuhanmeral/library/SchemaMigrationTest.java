package com.batuhanmeral.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The schema comes from the Flyway migrations, and Hibernate only checks that the entities match
 * it. These tests check the rules that live only in the database.
 */
@DataJpaTest
class SchemaMigrationTest implements DatabaseTest {

    @Autowired private Flyway flyway;

    @Autowired private JdbcTemplate jdbcTemplate;

    private long authorId;

    @BeforeEach
    void insertAnAuthor() {
        jdbcTemplate.update(
                "insert into authors (name, created_at, updated_at, created_by, updated_by)"
                        + " values ('Tolkien', current_timestamp, current_timestamp, 'test', 'test')");
        authorId = jdbcTemplate.queryForObject("select max(id) from authors", Long.class);
    }

    @Test
    void allMigrationsAreApplied() {
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getScript))
                .containsExactly(
                        "V1__create_schema.sql",
                        "V2__add_book_version.sql",
                        "V3__one_open_loan_per_member_and_book.sql",
                        "V4__trigram_index_on_book_title.sql");
    }

    @Test
    void booksInsertedWithoutAVersionStartAtVersionZero() {
        // Rows that existed before V2 get the column default.
        insertBook(1, 1);

        assertThat(jdbcTemplate.queryForObject("select version from books", Long.class)).isZero();
    }

    @Test
    void availableCopiesCannotExceedTotalCopies() {
        assertThatThrownBy(() -> insertBook(2, 3))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void availableCopiesCannotBeNegative() {
        assertThatThrownBy(() -> insertBook(2, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void loanStatusMustBeAKnownValue() {
        insertBook(1, 1);
        long bookId = jdbcTemplate.queryForObject("select max(id) from books", Long.class);
        jdbcTemplate.update(
                "insert into users (email, password, full_name, role, created_at, updated_at)"
                        + " values ('a@example.com', 'x', 'A', 'MEMBER', current_timestamp,"
                        + " current_timestamp)");
        long userId = jdbcTemplate.queryForObject("select max(id) from users", Long.class);

        assertThatThrownBy(() ->
                        jdbcTemplate.update(
                                "insert into loans (book_id, member_id, loan_date, due_date,"
                                        + " status, created_at, updated_at, created_by,"
                                        + " updated_by) values (?, ?, current_date,"
                                        + " current_date, 'LOST', current_timestamp,"
                                        + " current_timestamp, 'test', 'test')",
                                bookId,
                                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAuthorWithBooksCannotBeDeletedEvenWithoutTheServiceCheck() {
        insertBook(1, 1);

        assertThatThrownBy(() -> jdbcTemplate.update("delete from authors where id = ?", authorId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_books_author");
    }

    @Test
    void aMemberCannotHaveTwoOpenLoansOfTheSameBook() {
        long bookId = insertBook(2, 2);
        long memberId = insertMember();
        insertLoan(bookId, memberId, "ACTIVE");

        assertThatThrownBy(() -> insertLoan(bookId, memberId, "OVERDUE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_loans_open_member_book");
    }

    @Test
    void returnedLoansDoNotCountAsOpen() {
        // The index is partial: returned loans are not in it, so a member may borrow the same
        // book again, and again.
        long bookId = insertBook(1, 1);
        long memberId = insertMember();
        insertLoan(bookId, memberId, "RETURNED");
        insertLoan(bookId, memberId, "RETURNED");
        insertLoan(bookId, memberId, "ACTIVE");

        assertThat(jdbcTemplate.queryForObject("select count(*) from loans", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void twoMembersMayBorrowTheSameBook() {
        long bookId = insertBook(2, 2);
        insertLoan(bookId, insertMember(), "ACTIVE");
        insertLoan(bookId, insertMember(), "ACTIVE");

        assertThat(jdbcTemplate.queryForObject("select count(*) from loans", Integer.class))
                .isEqualTo(2);
    }

    private long insertMember() {
        jdbcTemplate.update(
                "insert into users (email, password, full_name, role, created_at, updated_at)"
                        + " values (gen_random_uuid() || '@example.com', 'x', 'A', 'MEMBER',"
                        + " current_timestamp, current_timestamp)");
        return jdbcTemplate.queryForObject("select max(id) from users", Long.class);
    }

    private void insertLoan(long bookId, long memberId, String status) {
        jdbcTemplate.update(
                "insert into loans (book_id, member_id, loan_date, due_date, return_date, status,"
                        + " created_at, updated_at, created_by, updated_by) values (?, ?,"
                        + " current_date, current_date, case when ? = 'RETURNED' then current_date"
                        + " end, ?, current_timestamp, current_timestamp, 'test', 'test')",
                bookId,
                memberId,
                status,
                status);
    }

    private long insertBook(int totalCopies, int availableCopies) {
        jdbcTemplate.update(
                "insert into books (title, total_copies, available_copies, author_id, created_at,"
                        + " updated_at, created_by, updated_by) values ('The Hobbit', ?, ?, ?,"
                        + " current_timestamp, current_timestamp, 'test', 'test')",
                totalCopies,
                availableCopies,
                authorId);
        return jdbcTemplate.queryForObject("select max(id) from books", Long.class);
    }
}
