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
 * Kısmi unique index (V3), bir üyenin aynı kitapta ikinci açık ödüncüne karşı son korumadır. Spy
 * kontrolü "açık ödünç yok" diye cevaplatır; veritabanı yine reddeder ve istemci aynı cevabı alır.
 *
 * <p>PostgreSQL gerekir: H2'de kısmi index yok.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenLoanIndexTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private UserRepository userRepository;

    /** Gerçek repository; sadece aşağıdaki metod farklı cevap verir. */
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
        // Başarısız istek hiçbir şey değiştirmedi: tek ödünç, tek kopya.
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
