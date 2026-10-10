package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The partial unique index (V3) is the last guard against a second open loan of the same book for
 * one member. LoanService checks the rule first, so the index can only be reached when that check
 * is missed. A spy on the repository makes the check answer "no open loan", as a bug or a future
 * code path that forgets the check would: the database still refuses, and the client gets the same
 * answer as from the service check.
 *
 * <p>This needs PostgreSQL: H2 has no partial indexes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenLoanIndexTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private UserRepository userRepository;

    /** A real repository; only the one method stubbed below answers differently. */
    @MockitoSpyBean private LoanRepository loanRepository;

    private long bookId;
    private long memberId;

    @BeforeEach
    void saveABookWithTwoCopiesAndAMember() {
        Author author = authorRepository.save(new Author("Frank Herbert"));
        Book book = new Book("Dune", 2);
        book.setAuthor(author);
        bookId = bookRepository.save(book).getId();
        memberId =
                userRepository
                        .save(new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER))
                        .getId();
    }

    @Test
    void withoutTheServiceCheckTheIndexStillRefusesASecondOpenLoan() {
        doReturn(false)
                .when(loanRepository)
                .existsByMemberIdAndBookIdAndStatusIn(anyLong(), anyLong(), any());

        assertThat(borrow()).hasStatus(HttpStatus.CREATED);
        MvcTestResult second = borrow();

        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("BOOK_ALREADY_BORROWED");
        // The failed request changed nothing: one loan, one copy taken.
        assertThat(loanRepository.count()).isEqualTo(1);
        assertThat(bookRepository.findById(bookId).orElseThrow().getAvailableCopies()).isEqualTo(1);
    }

    private MvcTestResult borrow() {
        return mvc.post()
                .uri("/api/v1/loans")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\": %d}".formatted(bookId))
                .exchange();
    }
}
