package com.batuhanmeral.library.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.JwtService;
import com.batuhanmeral.library.auth.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryApiIntegrationTest implements DatabaseTest {

    private static final String CATEGORIES = "/api/v1/categories";

    @Autowired private MockMvcTester mvc;

    @Autowired private JwtService jwt;

    @Autowired private CategoryRepository categoryRepository;

    @Test
    void createReturns201WithLocationHeaderAndTheNewCategory() {
        MvcTestResult result =
                post("{\"name\": \"Science\", \"description\": \"Popular science\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idOf(result);
        assertThat(result)
                .headers()
                .hasValue("Location", "http://localhost" + CATEGORIES + "/" + id);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Science");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Popular science");
        assertThat(result).bodyJson().extractingPath("$.createdAt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.updatedAt").isNotNull();
    }

    @Test
    void createIgnoresAnIdSentInTheRequestBody() {
        long existingId = idOf(post("{\"name\": \"History\"}"));

        MvcTestResult result = post("{\"id\": " + existingId + ", \"name\": \"Poetry\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(idOf(result)).isNotEqualTo(existingId);
        assertThat(categoryRepository.findById(existingId))
                .get()
                .extracting(Category::getName)
                .isEqualTo("History");
    }

    @Test
    void getByIdReturnsTheCategory() {
        long id = idOf(post("{\"name\": \"Science\"}"));

        assertThat(mvc.get().uri(CATEGORIES + "/{id}", id))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Science");
    }

    @Test
    void getByIdReturns404ProblemWhenTheCategoryDoesNotExist() {
        MvcTestResult result = mvc.get().uri(CATEGORIES + "/{id}", 9999).exchange();

        assertProblem(result, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Category with id 9999 was not found.");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(CATEGORIES + "/9999");
    }

    @Test
    void listReturnsAllCategories() {
        post("{\"name\": \"Science\"}");
        post("{\"name\": \"History\"}");

        assertThat(mvc.get().uri(CATEGORIES))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactlyInAnyOrder("Science", "History");
    }

    @Test
    void updateChangesNameAndDescription() {
        long id = idOf(post("{\"name\": \"Sciense\"}"));

        MvcTestResult result = put(id, "{\"name\": \"Science\", \"description\": \"Fixed typo\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Science");
        assertThat(categoryRepository.findById(id))
                .get()
                .extracting(Category::getDescription)
                .isEqualTo("Fixed typo");
    }

    @Test
    void updateKeepingTheSameNameSucceeds() {
        long id = idOf(post("{\"name\": \"Science\"}"));

        assertThat(put(id, "{\"name\": \"Science\", \"description\": \"New description\"}"))
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void updateReturns404ProblemWhenTheCategoryDoesNotExist() {
        assertProblem(
                put(9999, "{\"name\": \"Science\"}"), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    @Test
    void deleteReturns204AndRemovesTheCategory() {
        long id = idOf(post("{\"name\": \"Science\"}"));

        assertThat(
                        mvc.delete()
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                                .uri(CATEGORIES + "/{id}", id))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(categoryRepository.existsById(id)).isFalse();
    }

    @Test
    void deleteReturns404ProblemWhenTheCategoryDoesNotExist() {
        assertProblem(
                mvc.delete()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(CATEGORIES + "/{id}", 9999)
                        .exchange(),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"name\": \"\"}",
                "{\"name\": \"   \"}",
                "{\"name\": null, \"description\": \"No name\"}"
            })
    void createReturns400WhenTheNameIsMissingOrBlank(String json) {
        MvcTestResult result = post(json);

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("must not be blank");
        assertThat(categoryRepository.count()).isZero();
    }

    @Test
    void createReturns400WhenTheNameIsLongerThan100Characters() {
        assertThat(post("{\"name\": \"" + "a".repeat(100) + "\"}")).hasStatus(HttpStatus.CREATED);

        MvcTestResult result = post("{\"name\": \"" + "b".repeat(101) + "\"}");

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("size must be between 0 and 100");
    }

    @Test
    void validationMessagesAreInEnglishWhateverLanguageTheClientAsksFor() {
        MvcTestResult result =
                mvc.post()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(CATEGORIES)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}")
                        .exchange();

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("must not be blank");
    }

    @Test
    void createReportsEveryInvalidField() {
        String json = "{\"name\": \"\", \"description\": \"" + "d".repeat(501) + "\"}";

        MvcTestResult result = post(json);

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("name", "description");
    }

    @Test
    void updateReturns400WhenTheNameIsMissing() {
        long id = idOf(post("{\"name\": \"Science\"}"));

        assertProblem(
                put(id, "{\"description\": \"No name\"}"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        assertThat(categoryRepository.findById(id))
                .get()
                .extracting(Category::getName)
                .isEqualTo("Science");
    }

    @Test
    void createReturns409WhenTheNameIsAlreadyTaken() {
        post("{\"name\": \"Science\"}");

        MvcTestResult result = post("{\"name\": \"Science\"}");

        assertProblem(result, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A category named 'Science' already exists.");
    }

    @Test
    void updateReturns409WhenAnotherCategoryHasTheName() {
        post("{\"name\": \"Science\"}");
        long id = idOf(post("{\"name\": \"History\"}"));

        assertProblem(
                put(id, "{\"name\": \"Science\"}"), HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    @Test
    void malformedJsonReturns400Problem() {
        assertProblem(post("{\"name\": "), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }

    @Test
    void nonNumericIdReturns400Problem() {
        assertProblem(
                mvc.get().uri(CATEGORIES + "/abc").exchange(),
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST");
    }

    /** Spring'in mesajı değeri tekrarlar; istemcinin gönderdiği hiçbir şey metne girmemeli. */
    @Test
    void aValueThatIsNotANumberIsNotRepeatedInTheDetail() {
        MvcTestResult result = mvc.get().uri(CATEGORIES + "/{id}", "abc<b>").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The path variable 'id' has an invalid value.");
    }

    @Test
    void anUnknownPathIsNotRepeatedInTheDetail() {
        MvcTestResult result = mvc.get().uri(CATEGORIES + "/1/does-not-exist").exchange();

        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("There is no resource at this path.");
    }

    @Test
    void unknownPathReturns404Problem() {
        // Herkese açık önekin altındaki yol Spring MVC'ye ulaşır ve handler bulunmaz; diğer
        // bilinmeyen yolları security kuralları reddeder.
        assertProblem(
                mvc.get().uri(CATEGORIES + "/1/does-not-exist").exchange(),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void unsupportedMethodReturns405Problem() {
        assertProblem(
                mvc.delete()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(CATEGORIES)
                        .exchange(),
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupportedContentTypeReturns415Problem() {
        MvcTestResult result =
                mvc.post()
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                        .uri(CATEGORIES)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Science")
                        .exchange();

        assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
    }

    private MvcTestResult post(String json) {
        return mvc.post()
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                .uri(CATEGORIES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult put(long id, String json) {
        return mvc.put()
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin(jwt))
                .uri(CATEGORIES + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }

    private static long idOf(MvcTestResult result) {
        String json =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }
}
