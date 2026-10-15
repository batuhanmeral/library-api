package com.batuhanmeral.library.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.DatabaseTest;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

/**
 * Çalışan uygulamanın audit izi: hangi çağrı hangi hedefle satır yazar, aspect diğer proxy'ler
 * arasında nerede durur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(AuditTrailTest.CommitFailureConfig.class)
class AuditTrailTest implements DatabaseTest {

    @Autowired private MockMvcTester mvc;

    @Autowired private ApplicationContext context;

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

        // Ödüncün hedefi kitaptır: reddedilen ödüncün id'si yok. Aktör token'daki üye.
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

        // Kayıtta giriş yapan yok, aktör "system".
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

        // Deneyen yazılmaz: adres kimseye ait olmayabilir ve kişisel veridir.
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
     * Değişiklik ancak commit'ten sonra başarılıdır; commit'i düşen çağrı hata olarak audit edilir.
     */
    @Test
    void aCommitThatFailsIsAuditedAsAFailure(CapturedOutput output) {
        assertThatThrownBy(commitFailingService::save).isInstanceOf(IllegalStateException.class);

        assertThat(output)
                .contains("action=CATEGORY_CREATE target=- actor=system outcome=FAILURE")
                .doesNotContain("action=CATEGORY_CREATE target=- actor=system outcome=SUCCESS");
    }

    /**
     * Hedef ifadeleri ilk çağrıda parse edilir; bu test hepsini şimdi parse eder, yazım hatası
     * build'i düşürsün.
     */
    @Test
    void everyAuditTargetInTheApplicationParses() {
        SpelExpressionParser parser = new SpelExpressionParser();
        List<Auditable> annotations =
                Arrays.stream(context.getBeanDefinitionNames())
                        .map(context::getType)
                        .filter(type ->
                                type != null && type.getName().startsWith("com.batuhanmeral"))
                        // Proxy sınıfı metodları anotasyonsuz override eder.
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
     * Bir bean'in proxy sırası, en dıştan: süre, audit, transaction. {@code @Retryable} hep en
     * dışta, bu yüzden ödünç LoanController'da audit edilir.
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

    /** İsteği admin olarak gönderir. */
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

    /** Bu id'li isteğe hizmet ederken yazılan audit satırları. */
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

    /** Metod normal döndükten sonra commit'i düşen transaction'lı metod. */
    static class CommitFailingService {

        // Herhangi bir eylem olur; bu bean sadece bu testte var.
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
