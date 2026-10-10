package com.batuhanmeral.library.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.category.CategoryRepository;
import com.batuhanmeral.library.category.CategoryService;
import com.batuhanmeral.library.common.logging.RequestIdFilter;
import com.batuhanmeral.library.loan.LoanController;
import com.batuhanmeral.library.loan.LoanFacade;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

/**
 * The audit trail of the running application: which calls write an audit line, with which target,
 * and where the audit aspect sits among the other proxies of a bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(AuditTrailTest.CommitFailureConfig.class)
class AuditTrailTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private ApplicationContext context;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private AuthorRepository authorRepository;

    @Autowired private BookRepository bookRepository;

    @Autowired private CategoryRepository categoryRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private CategoryService categoryService;

    @Autowired private LoanFacade loanFacade;

    @Autowired private LoanController loanController;

    @Autowired private CommitFailingService commitFailingService;

    @Autowired private JwtService jwt;

    private static final String ADMIN_ACTOR = "actor=user:" + TestTokens.ADMIN_ID;

    @BeforeEach
    void cleanDatabase() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate,
                "loans",
                "book_categories",
                "books",
                "authors",
                "categories",
                "users");
    }

    @Test
    void creatingUpdatingAndDeletingACategoryIsAudited(CapturedOutput output) {
        long id = idOf(send(mvc.post(), "/api/v1/categories", "{\"name\": \"Science\"}", "a-1"));
        send(mvc.put(), "/api/v1/categories/" + id, "{\"name\": \"Popular science\"}", "a-2");
        send(mvc.delete(), "/api/v1/categories/" + id, null, "a-3");

        assertThat(auditLines(output, "a-1"))
                .singleElement()
                .asString()
                .contains(
                        "action=CATEGORY_CREATE target="
                                + id
                                + " "
                                + ADMIN_ACTOR
                                + " outcome=SUCCESS");
        assertThat(auditLines(output, "a-2"))
                .singleElement()
                .asString()
                .contains(
                        "action=CATEGORY_UPDATE target="
                                + id
                                + " "
                                + ADMIN_ACTOR
                                + " outcome=SUCCESS");
        assertThat(auditLines(output, "a-3"))
                .singleElement()
                .asString()
                .contains(
                        "action=CATEGORY_DELETE target="
                                + id
                                + " "
                                + ADMIN_ACTOR
                                + " outcome=SUCCESS");
    }

    @Test
    void aRefusedChangeIsAuditedAsAFailure(CapturedOutput output) {
        Author author = authorRepository.save(new Author("Frank Herbert"));
        bookRepository.save(bookBy(author));

        MvcTestResult result = send(mvc.delete(), "/api/v1/authors/" + author.getId(), null, "a-4");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(auditLines(output, "a-4"))
                .singleElement()
                .asString()
                .contains(
                        "action=AUTHOR_DELETE target="
                                + author.getId()
                                + " "
                                + ADMIN_ACTOR
                                + " outcome=FAILURE reason=RESOURCE_IN_USE");
    }

    @Test
    void aBookIsAuditedWithTheIdItGot(CapturedOutput output) {
        Author author = authorRepository.save(new Author("Frank Herbert"));
        String body =
                "{\"title\": \"Dune\", \"totalCopies\": 1, \"authorId\": " + author.getId() + "}";

        long id = idOf(send(mvc.post(), "/api/v1/books", body, "a-6"));
        send(mvc.patch(), "/api/v1/books/" + id, "{\"publishedYear\": 1965}", "a-7");

        assertThat(auditLines(output, "a-6"))
                .singleElement()
                .asString()
                .contains(
                        "action=BOOK_CREATE target=" + id + " " + ADMIN_ACTOR + " outcome=SUCCESS");
        assertThat(auditLines(output, "a-7"))
                .singleElement()
                .asString()
                .contains("action=BOOK_UPDATE target=" + id);
    }

    @Test
    void borrowingAndReturningAreAudited(CapturedOutput output) {
        Book book = bookRepository.save(bookBy(authorRepository.save(new Author("F. Herbert"))));
        User member =
                userRepository.save(new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER));
        String body = "{\"bookId\": " + book.getId() + "}";
        String token = TestTokens.member(jwt, member.getId());
        String actor = "actor=user:" + member.getId();

        long loanId = idOf(send(mvc.post(), "/api/v1/loans", body, "a-8", token));
        send(mvc.post(), "/api/v1/loans/" + loanId + "/return", null, "a-9", token);
        send(mvc.post(), "/api/v1/loans/" + loanId + "/return", null, "a-10", token);

        // The target of a borrow is the book: a refused borrow has no loan id to name. The actor
        // is the member, read from the token.
        assertThat(auditLines(output, "a-8"))
                .singleElement()
                .asString()
                .contains("action=LOAN_BORROW target=" + book.getId() + " " + actor);
        assertThat(auditLines(output, "a-9"))
                .singleElement()
                .asString()
                .contains("action=LOAN_RETURN target=" + loanId + " " + actor + " outcome=SUCCESS");
        assertThat(auditLines(output, "a-10"))
                .singleElement()
                .asString()
                .contains("outcome=FAILURE reason=LOAN_ALREADY_RETURNED");
    }

    @Test
    void startingTheOverdueCheckIsAudited(CapturedOutput output) {
        send(mvc.post(), "/api/v1/admin/jobs/overdue-check", null, "a-11");

        assertThat(auditLines(output, "a-11"))
                .singleElement()
                .asString()
                .contains(
                        "action=OVERDUE_CHECK_START target=- " + ADMIN_ACTOR + " outcome=SUCCESS");
    }

    @Test
    void registeringIsAuditedWithTheNewUserAsTheTarget(CapturedOutput output) {
        String body =
                "{\"email\": \"ada@example.com\", \"password\": \"correct horse\","
                        + " \"fullName\": \"Ada\"}";

        long id = idOf(send(mvc.post(), "/api/v1/auth/register", body, "a-13", null));
        send(mvc.post(), "/api/v1/auth/register", body, "a-14", null);

        // Nobody is logged in while registering, so the actor is "system".
        assertThat(auditLines(output, "a-13"))
                .singleElement()
                .asString()
                .contains("action=USER_REGISTER target=" + id + " actor=system outcome=SUCCESS");
        assertThat(auditLines(output, "a-14"))
                .singleElement()
                .asString()
                .contains(
                        "action=USER_REGISTER target=- actor=system outcome=FAILURE"
                                + " reason=DUPLICATE_RESOURCE");
    }

    @Test
    void failedLoginsAreAudited(CapturedOutput output) {
        send(
                mvc.post(),
                "/api/v1/auth/login",
                "{\"email\": \"nobody@example.com\", \"password\": \"wrong password\"}",
                "a-15",
                null);

        // Who tried is not written: the address may not belong to anyone, and it is personal
        // data. The request id leads to the API call line (method, path, status).
        assertThat(auditLines(output, "a-15"))
                .singleElement()
                .asString()
                .contains(
                        "action=LOGIN target=- actor=system outcome=FAILURE"
                                + " reason=BadCredentialsException");
    }

    @Test
    void readingIsNotAudited(CapturedOutput output) {
        categoryRepository.save(new Category("Science", null));

        send(mvc.get(), "/api/v1/categories", null, "a-12");

        assertThat(auditLines(output, "a-12")).isEmpty();
    }

    /**
     * A change is a success only once it is committed. The audit aspect wraps the transaction, so a
     * commit that fails after the method returned is audited as a failure.
     */
    @Test
    void aCommitThatFailsIsAuditedAsAFailure(CapturedOutput output) {
        assertThatThrownBy(commitFailingService::save).isInstanceOf(IllegalStateException.class);

        assertThat(output)
                .contains("action=CATEGORY_CREATE target=- actor=system outcome=FAILURE")
                .doesNotContain("action=CATEGORY_CREATE target=- actor=system outcome=SUCCESS");
    }

    /**
     * A target expression is only parsed when its method is first called. This parses every one in
     * the application now, so a typo fails the build instead of a request.
     */
    @Test
    void everyAuditTargetInTheApplicationParses() {
        SpelExpressionParser parser = new SpelExpressionParser();
        List<Auditable> annotations =
                Arrays.stream(context.getBeanDefinitionNames())
                        .map(context::getType)
                        .filter(type ->
                                type != null && type.getName().startsWith("com.batuhanmeral"))
                        // A proxy's class overrides the methods without their annotations.
                        .map(ClassUtils::getUserClass)
                        .distinct()
                        .flatMap(type -> Arrays.stream(type.getMethods()))
                        .map(method -> method.getAnnotation(Auditable.class))
                        .filter(annotation -> annotation != null)
                        .toList();

        assertThat(annotations).hasSizeGreaterThanOrEqualTo(AuditAction.values().length);
        for (Auditable annotation : annotations) {
            if (!annotation.target().isEmpty()) {
                assertThatCode(() -> parser.parseExpression(annotation.target()))
                        .as("target of %s", annotation.action())
                        .doesNotThrowAnyException();
            }
        }
    }

    /**
     * The order of the proxies of one bean, outermost first. On a service: timing, audit, then the
     * transaction. Spring's {@code @Retryable} always puts its interceptor in front of the advice a
     * bean already has, so on LoanFacade each attempt is timed on its own, and borrowing and
     * returning are audited one level up, on LoanController, outside the retries.
     */
    @Test
    void theAuditWrapsTheTransactionAndStaysOutsideTheRetries() {
        assertThat(adviceOf(categoryService))
                .containsSubsequence(
                        "executionTimeAspect", "auditAspect", "TransactionInterceptor");
        List<String> facade = adviceOf(loanFacade);
        assertThat(facade.getFirst()).isEqualTo("RetryAnnotationInterceptor");
        assertThat(facade).contains("executionTimeAspect").doesNotContain("auditAspect");
        assertThat(adviceOf(loanController)).contains("auditAspect");
    }

    /** Sends the request as an admin. */
    private MvcTestResult send(
            MockMvcTester.MockMvcRequestBuilder request,
            String uri,
            String json,
            String requestId) {
        return send(request, uri, json, requestId, TestTokens.admin(jwt));
    }

    private MvcTestResult send(
            MockMvcTester.MockMvcRequestBuilder request,
            String uri,
            String json,
            String requestId,
            String authorization) {
        request.uri(uri).header(RequestIdFilter.HEADER, requestId);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    /** The audit lines written while serving the request with this id. */
    private static List<String> auditLines(CapturedOutput output, String requestId) {
        return output.getOut()
                .lines()
                .filter(line -> line.contains("[" + requestId + "]"))
                .filter(line -> line.contains("action="))
                .toList();
    }

    private static List<String> adviceOf(Object bean) {
        return Arrays.stream(((Advised) bean).getAdvisors())
                .map(Advisor::getAdvice)
                .map(advice ->
                        advice instanceof AbstractAspectJAdvice aspectAdvice
                                ? aspectAdvice.getAspectName()
                                : advice.getClass().getSimpleName())
                .toList();
    }

    private static Book bookBy(Author author) {
        Book book = new Book("Dune", 1);
        book.setAuthor(author);
        return book;
    }

    private static long idOf(MvcTestResult result) {
        String json =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }

    /** A transactional method whose commit fails after the method itself returned normally. */
    static class CommitFailingService {

        // Any action will do; this bean exists only in this test.
        @Auditable(action = AuditAction.CATEGORY_CREATE)
        @Transactional
        public void save() {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            throw new IllegalStateException("commit failed");
                        }
                    });
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CommitFailureConfig {

        @Bean
        CommitFailingService commitFailingService() {
            return new CommitFailingService();
        }
    }
}
