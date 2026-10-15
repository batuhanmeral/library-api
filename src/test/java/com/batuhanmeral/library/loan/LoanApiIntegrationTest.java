package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Üyeler repository ile kaydedilir ve JwtService'in imzaladığı token'larla davranır; katalog admin
 * token'ıyla kurulur. "Bugün" uygulama saatinden okunur.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoanApiIntegrationTest implements DatabaseTest {

    private static final String LOANS = "/api/v1/loans";

    @Autowired private MockMvcTester mvc;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private UserRepository userRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private LoanRepository loanRepository;

    @Autowired private Clock clock;

    @Autowired private JwtService jwt;

    private long authorId;
    private long memberId;

    @BeforeEach
    void createAnAuthorAndAMember() {
        authorId = idOf(post("/api/v1/authors", "{\"name\": \"Frank Herbert\"}"));
        memberId = saveMember("ada@example.com");
    }

    @Test
    void borrowReturns201WithADueDateTwoWeeksLaterAndTakesACopy() {
        long bookId = createBook("Dune", 2);

        MvcTestResult result = borrow(bookId, memberId);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long loanId = idOf(result);
        assertThat(result)
                .headers()
                .hasValue("Location", "http://localhost" + LOANS + "/" + loanId);
        LocalDate today = today();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.loanDate").isEqualTo(today.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.dueDate")
                .isEqualTo(today.plusDays(14).toString());
        assertThat(result).bodyJson().extractingPath("$.returnDate").isNull();
        assertThat(result).bodyJson().extractingPath("$.book.title").isEqualTo("Dune");
        assertThat(result).bodyJson().extractingPath("$.member.fullName").isEqualTo("Reader");
        assertThat(availableCopies(bookId)).isEqualTo(1);
    }

    @Test
    void borrowReturns409WhenNoCopyIsAvailable() {
        long bookId = createBook("Dune", 1);
        borrow(bookId, saveMember("first@example.com"));

        MvcTestResult result = borrow(bookId, memberId);

        assertProblem(result, HttpStatus.CONFLICT, "BOOK_NOT_AVAILABLE");
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "loans")).isEqualTo(1);
    }

    @Test
    void borrowReturns422WhenTheMemberAlreadyHasThreeActiveLoans() {
        for (String title : new String[] {"Dune", "Dune Messiah", "Children of Dune"}) {
            assertThat(borrow(createBook(title, 1), memberId)).hasStatus(HttpStatus.CREATED);
        }
        long fourth = createBook("God Emperor of Dune", 1);

        MvcTestResult result = borrow(fourth, memberId);

        assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "LOAN_LIMIT_REACHED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The member already has 3 active loans, the most allowed.");
        // İstek bütünüyle reddedildi: kopya alınmadı.
        assertThat(availableCopies(fourth)).isEqualTo(1);
    }

    @Test
    void returnedLoansDoNotCountTowardsTheLimit() {
        long dune = createBook("Dune", 1);
        long loanId = idOf(borrow(dune, memberId));
        borrow(createBook("Dune Messiah", 1), memberId);
        borrow(createBook("Children of Dune", 1), memberId);
        returnLoan(loanId);

        assertThat(borrow(createBook("God Emperor of Dune", 1), memberId))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void borrowReturns422WhenTheMemberHasALoanPastItsDueDate() {
        // 20 gün önce 14 günlüğüne alındı; gece işi henüz çalışmadığı için ACTIVE, ama kural yine
        // geçerli.
        saveLoan(createBook("Dune", 1), today().minusDays(20));

        MvcTestResult result = borrow(createBook("Dune Messiah", 1), memberId);

        assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "MEMBER_HAS_OVERDUE_LOAN");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(
                        "The member has an overdue loan and cannot borrow until it is returned.");
    }

    @Test
    void borrowReturns422WhenTheMemberHasALoanMarkedOverdue() {
        long loanId = saveLoan(createBook("Dune", 1), today());
        jdbcTemplate.update("update loans set status = 'OVERDUE' where id = ?", loanId);

        assertProblem(
                borrow(createBook("Dune Messiah", 1), memberId),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "MEMBER_HAS_OVERDUE_LOAN");
    }

    @Test
    void aLoanDueTodayIsNotOverdue() {
        saveLoan(createBook("Dune", 1), today().minusDays(14));

        assertThat(borrow(createBook("Dune Messiah", 1), memberId)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void borrowReturns422WhenTheBorrowerIsNoLongerAMember() {
        // Admin token'ı buraya gelemez (403), ama rol değişince eski token süresi dolana kadar
        // geçerli kalır. Service güncel rolü kontrol eder.
        long bookId = createBook("Dune", 1);
        long adminId =
                userRepository
                        .save(new User("admin@example.com", "{noop}x", "Admin", Role.ADMIN))
                        .getId();

        MvcTestResult result = borrow(bookId, adminId);

        assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "NOT_A_MEMBER");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Only members can borrow books.");
        assertThat(availableCopies(bookId)).isEqualTo(1);
    }

    @Test
    void aMemberCannotBorrowASecondCopyOfABookTheyHaveOnLoan() {
        long bookId = createBook("Dune", 3);
        long loanId = idOf(borrow(bookId, memberId));

        MvcTestResult again = borrow(bookId, memberId);

        assertProblem(again, HttpStatus.CONFLICT, "BOOK_ALREADY_BORROWED");
        assertThat(again)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The member already has this book on loan.");
        assertThat(availableCopies(bookId)).isEqualTo(2);

        // Kopya dönünce üye kitabı tekrar alabilir.
        returnLoan(loanId);
        assertThat(borrow(bookId, memberId)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void borrowReturns404ForAnUnknownBookOrMemberAnd400ForAMissingBookId() {
        long bookId = createBook("Dune", 1);

        MvcTestResult unknownMember = borrow(bookId, 9999);
        assertProblem(unknownMember, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(unknownMember)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Member with id 9999 was not found.");
        assertProblem(borrow(9999, memberId), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");

        MvcTestResult missing =
                mvc.post()
                        .uri(LOANS)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .exchange();
        assertProblem(missing, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(missing)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("bookId");
    }

    @Test
    void theBorrowerIsTheTokensUserWhateverTheBodySays() {
        long grace = saveMember("grace@example.com");

        MvcTestResult result =
                mvc.post()
                        .uri(LOANS)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"bookId\": %d, \"memberId\": %d}"
                                        .formatted(createBook("Dune", 1), grace))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.member.id")
                .isEqualTo(Math.toIntExact(memberId));
    }

    @Test
    void returnMarksTheLoanReturnedAndPutsTheCopyBack() {
        long bookId = createBook("Dune", 1);
        long loanId = idOf(borrow(bookId, memberId));

        MvcTestResult result = returnLoan(loanId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RETURNED");
        assertThat(result).bodyJson().extractingPath("$.returnDate").isEqualTo(today().toString());
        assertThat(availableCopies(bookId)).isEqualTo(1);
    }

    @Test
    void returningTwiceReturns409AndDoesNotAddACopy() {
        long bookId = createBook("Dune", 1);
        long loanId = idOf(borrow(bookId, memberId));
        returnLoan(loanId);

        MvcTestResult result = returnLoan(loanId);

        assertProblem(result, HttpStatus.CONFLICT, "LOAN_ALREADY_RETURNED");
        assertThat(availableCopies(bookId)).isEqualTo(1);
    }

    @Test
    void getByIdReturnsTheLoanOr404() {
        long loanId = idOf(borrow(createBook("Dune", 1), memberId));

        assertThat(getLoan(loanId, TestTokens.member(jwt, memberId)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.book.title")
                .isEqualTo("Dune");
        assertProblem(
                getLoan(9999, TestTokens.member(jwt, memberId)),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
        assertProblem(returnLoan(9999), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    @Test
    void aMemberCannotSeeOrReturnAnotherMembersLoan() {
        long bookId = createBook("Dune", 1);
        long loanId = idOf(borrow(bookId, memberId));
        String grace = TestTokens.member(jwt, saveMember("grace@example.com"));

        assertProblem(getLoan(loanId, grace), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertProblem(returnLoanAs(loanId, grace), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(availableCopies(bookId)).isZero();
    }

    @Test
    void anAdminCanSeeAndReturnAnyLoan() {
        long bookId = createBook("Dune", 1);
        long loanId = idOf(borrow(bookId, memberId));

        assertThat(getLoan(loanId, TestTokens.admin(jwt))).hasStatus(HttpStatus.OK);
        assertThat(returnLoanAs(loanId, TestTokens.admin(jwt))).hasStatus(HttpStatus.OK);
        assertThat(availableCopies(bookId)).isEqualTo(1);
    }

    @Test
    void myLoansListsOnlyTheTokensOwnLoans() {
        long grace = saveMember("grace@example.com");
        long first = idOf(borrow(createBook("Dune", 1), memberId));
        borrow(createBook("Dune Messiah", 1), memberId);
        borrow(createBook("Children of Dune", 1), grace);
        returnLoanAs(first, TestTokens.member(jwt, memberId));

        assertThat(titlesAs(TestTokens.member(jwt, memberId), LOANS + "/me"))
                .containsExactly("Dune Messiah", "Dune");
        assertThat(titlesAs(TestTokens.member(jwt, memberId), LOANS + "/me?status=ACTIVE"))
                .containsExactly("Dune Messiah");
        assertThat(titlesAs(TestTokens.member(jwt, grace), LOANS + "/me"))
                .containsExactly("Children of Dune");
    }

    @Test
    void listFiltersByStatusAndMemberNewestFirst() {
        long other = saveMember("grace@example.com");
        long first = idOf(borrow(createBook("Dune", 1), memberId));
        borrow(createBook("Dune Messiah", 1), memberId);
        borrow(createBook("Children of Dune", 1), other);
        returnLoan(first);

        assertThat(titles(LOANS + "?memberId={id}", memberId))
                .containsExactly("Dune Messiah", "Dune");
        assertThat(titles(LOANS + "?memberId={id}&status=ACTIVE", memberId))
                .containsExactly("Dune Messiah");
        assertThat(titles(LOANS + "?status=RETURNED")).containsExactly("Dune");
        assertThat(mvc.get().uri(LOANS).header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt)))
                .bodyJson()
                .extractingPath("$.totalElements")
                .isEqualTo(3);
    }

    @Test
    void listReturns400ForAnUnknownStatusOrSortField() {
        MvcTestResult unknownStatus = asAdmin(mvc.get().uri(LOANS + "?status=LOST"));
        assertProblem(unknownStatus, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        // Spring'in mesajı Java tipini ve değeri yazar; cevapta sabit metin var.
        assertThat(unknownStatus)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("has an invalid value");
        assertThat(unknownStatus).bodyText().doesNotContain("com.batuhanmeral", "LOST");
        assertProblem(
                asAdmin(mvc.get().uri(LOANS + "?sort=member.password")),
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER");
    }

    @Test
    void aBookWithAnActiveLoanCannotBeDeleted() {
        long bookId = createBook("Dune", 1);
        borrow(bookId, memberId);

        MvcTestResult result = asAdmin(mvc.delete().uri("/api/v1/books/{id}", bookId));

        assertProblem(result, HttpStatus.CONFLICT, "RESOURCE_IN_USE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Book with id " + bookId + " has active loans and cannot be deleted.");
    }

    @Test
    void aBookWithOnlyReturnedLoansCannotBeDeletedEither() {
        long bookId = createBook("Dune", 1);
        returnLoan(idOf(borrow(bookId, memberId)));

        MvcTestResult result = asAdmin(mvc.delete().uri("/api/v1/books/{id}", bookId));

        assertProblem(result, HttpStatus.CONFLICT, "RESOURCE_IN_USE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Book with id " + bookId + " has a loan history and cannot be deleted.");
    }

    @Test
    void theTotalCannotDropBelowTheCopiesOnLoan() {
        long bookId = createBook("Dune", 2);
        borrow(bookId, memberId);
        borrow(bookId, saveMember("grace@example.com"));

        MvcTestResult result =
                asAdmin(
                        mvc.patch()
                                .uri("/api/v1/books/{id}", bookId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"totalCopies\": 1}"));

        assertProblem(result, HttpStatus.CONFLICT, "COPIES_ON_LOAN");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private long saveMember(String email) {
        return userRepository
                .save(new User(email, "{noop}not-a-real-hash", "Reader", Role.MEMBER))
                .getId();
    }

    private long saveLoan(long bookId, LocalDate loanDate) {
        Loan loan =
                new Loan(
                        bookRepository.getReferenceById(bookId),
                        userRepository.getReferenceById(memberId),
                        loanDate,
                        14);
        return loanRepository.save(loan).getId();
    }

    private long createBook(String title, int copies) {
        return idOf(
                post(
                        "/api/v1/books",
                        "{\"title\": \"%s\", \"totalCopies\": %d, \"authorId\": %d}"
                                .formatted(title, copies, authorId)));
    }

    private int availableCopies(long bookId) {
        return jdbcTemplate.queryForObject(
                "select available_copies from books where id = ?", Integer.class, bookId);
    }

    private java.util.List<String> titles(String uriTemplate, Object... uriVariables) {
        return titlesAs(TestTokens.admin(jwt), uriTemplate, uriVariables);
    }

    private java.util.List<String> titlesAs(
            String authorization, String uriTemplate, Object... uriVariables) {
        MvcTestResult result =
                mvc.get()
                        .uri(uriTemplate, uriVariables)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return JsonPath.read(body(result), "$.content[*].book.title");
    }

    /** Üye olarak ödünç alır: token alanı, gövde sadece kitabı söyler. */
    private MvcTestResult borrow(long bookId, long memberId) {
        return mvc.post()
                .uri(LOANS)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.member(jwt, memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\": %d}".formatted(bookId))
                .exchange();
    }

    /** Ödünçlerin çoğunu alan ana test üyesi olarak iade eder. */
    private MvcTestResult returnLoan(long loanId) {
        return returnLoanAs(loanId, TestTokens.member(jwt, memberId));
    }

    private MvcTestResult returnLoanAs(long loanId, String authorization) {
        return mvc.post()
                .uri(LOANS + "/{id}/return", loanId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
    }

    private MvcTestResult getLoan(long loanId, String authorization) {
        return mvc.get()
                .uri(LOANS + "/{id}", loanId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
    }

    /** Katalog değişiklikleri admin için. */
    private MvcTestResult post(String uri, String json) {
        return asAdmin(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MvcTestResult asAdmin(
            org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder
                    request) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt)).exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }

    private static long idOf(MvcTestResult result) {
        Number id = JsonPath.read(body(result), "$.id");
        return id.longValue();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
