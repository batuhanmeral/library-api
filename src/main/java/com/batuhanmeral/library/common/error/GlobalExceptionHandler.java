package com.batuhanmeral.library.common.error;

import com.batuhanmeral.library.auth.InvalidTokenException;
import com.batuhanmeral.library.common.ratelimit.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Controller'dan çıkan her hatayı {@code errorCode} alanlı RFC 9457 problem cevabına çevirir.
 * Spring MVC hatalarını üst sınıf karşılar, burada hata kodu eklenir.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_CODE = "errorCode";

    /**
     * Service'in de kontrol ettiği kuralları koruyan constraint'ler. Veritabanı reddederse istemci
     * kuralın kendi kodunu alır; diğerleri DATA_CONFLICT.
     */
    private static final Map<String, RuleViolation> RULE_CONSTRAINTS =
            Map.of(
                    "uk_loans_open_member_book",
                    new RuleViolation(
                            ErrorCode.BOOK_ALREADY_BORROWED,
                            "The member already has this book on loan."));

    private record RuleViolation(ErrorCode errorCode, String detail) {}

    /**
     * SQLState 22021: PostgreSQL metinde NUL karakteri reddeder. İstemci gönderdi, yani 400. Sınıf
     * 22'nin diğer hataları uygulamada eksik kontrol demektir.
     */
    private static final String UNSTORABLE_CHARACTER = "22021";

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        return problem(ex.getErrorCode(), ex.getMessage(), request);
    }

    /** Retry-After ile 429: pencerenin bitmesine kalan saniye, yukarı yuvarlanmış. */
    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> handleTooManyRequests(
            TooManyRequestsException ex, HttpServletRequest request) {
        long millis = ex.getRetryAfter().toMillis();
        long seconds = Math.max(1, (millis + 999) / 1000);
        return ResponseEntity.status(ex.getErrorCode().status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem(ex.getErrorCode(), ex.getMessage(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        if (isUnstorableCharacter(ex)) {
            return unstorableValue(request);
        }
        // Veritabanı mesajı tabloları ve reddedilen değerleri içerir; ne istemciye ne log'a gider,
        // constraint adı yeter.
        String constraint =
                ex.getCause() instanceof ConstraintViolationException violation
                        ? violation.getConstraintName()
                        : null;
        log.warn("Database constraint violated: {}", constraint == null ? "unknown" : constraint);
        RuleViolation rule = constraint == null ? null : RULE_CONSTRAINTS.get(constraint);
        if (rule != null) {
            return problem(rule.errorCode(), rule.detail(), request);
        }
        return problem(
                ErrorCode.DATA_CONFLICT, "The request conflicts with existing data.", request);
    }

    /**
     * Okuma ile yazma arasında başka transaction aynı satırı değiştirdi (bkz. Book.version). Hiçbir
     * şey kaydedilmedi, istemci tekrar deneyebilir.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Optimistic locking conflict: {}", ex.getClass().getSimpleName());
        return problem(
                ErrorCode.CONCURRENT_UPDATE,
                "The resource was changed by another request. Reload it and try again.",
                request);
    }

    /**
     * 401: token yok, token bozuk ya da giriş başarısız. WWW-Authenticate şemayı (RFC 9110) ve
     * bozuk token hatasını (RFC 6750) bildirir.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        if (ex instanceof InternalAuthenticationServiceException && isUnstorableCharacter(ex)) {
            // Spring Security kullanıcı aramasındaki her hatayı sarar, bunu da.
            return ResponseEntity.badRequest().body(unstorableValue(request));
        }
        if (ex instanceof InternalAuthenticationServiceException) {
            // İstemcinin bilgisi değil, hesap yüklenemedi (örn. veritabanı kapalı); 401 arızayı
            // gizlerdi.
            log.error("Authentication failed inside the server", ex);
            return ResponseEntity.internalServerError()
                    .body(
                            problem(
                                    ErrorCode.INTERNAL_ERROR,
                                    "An unexpected error occurred.",
                                    request));
        }
        ErrorCode code;
        String detail;
        String challenge = "Bearer";
        if (ex instanceof InvalidTokenException) {
            code = ErrorCode.INVALID_TOKEN;
            detail = ex.getMessage();
            challenge = "Bearer error=\"invalid_token\"";
        } else if (ex instanceof BadCredentialsException) {
            code = ErrorCode.INVALID_CREDENTIALS;
            detail = "The email or password is wrong.";
        } else {
            code = ErrorCode.AUTHENTICATION_REQUIRED;
            detail = "Log in and send the access token: Authorization: Bearer <token>.";
        }
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
                .body(problem(code, detail, request));
    }

    /** 403: geçerli token, ama SecurityConfig'teki kuralın istediği rol yok. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(ErrorCode.ACCESS_DENIED, "You do not have permission to do this.", request);
    }

    /**
     * 400: StrictHttpFirewall isteği reddetti (";" ya da kodlanmış "/", "../", kontrol karakterli
     * header). Güvenilmeyen yol cevaba ve log'a yazılmaz.
     */
    @ExceptionHandler(RequestRejectedException.class)
    ProblemDetail handleRequestRejected(RequestRejectedException ex) {
        log.info("Request rejected by the firewall");
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        ErrorCode.REQUEST_REJECTED.status(), "The request was rejected.");
        problem.setProperty(ERROR_CODE, ErrorCode.REQUEST_REJECTED.name());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldViolation(error.getField(), messageOf(error)))
                        .toList();
        ProblemDetail body = ex.getBody();
        body.setDetail("The request has invalid fields.");
        body.setProperty(ERROR_CODE, ErrorCode.VALIDATION_FAILED.name());
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Tipine çevrilemeyen path variable ya da parametre ("abc" id). İstemci sadece adı görür,
     * değeri değil.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = "A value has an invalid type.";
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            String kind =
                    mismatch.getParameter().hasParameterAnnotation(PathVariable.class)
                            ? "path variable"
                            : "parameter";
            detail = "The %s '%s' has an invalid value.".formatted(kind, mismatch.getName());
        }
        ProblemDetail body = createProblemDetail(ex, status, detail, null, null, request);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Yol için controller ya da statik dosya yok; Spring'in mesajı yolu tekrarlardı. */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ex.getBody().setDetail("There is no resource at this path.");
        return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
    }

    /** Üst sınıfın her cevabı buradan geçer; gövde null ise problem hatanın kendisinden alınır. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail problem) {
            if (!hasErrorCode(problem)) {
                problem.setProperty(ERROR_CODE, ErrorCode.forStatus(statusCode).name());
            }
            if (problem.getInstance() == null && request instanceof ServletWebRequest web) {
                problem.setInstance(URI.create(web.getRequest().getRequestURI()));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /** Binding hatası çevrilemeyen değerdir (örn. "LOST" durumu); sabit metin yeter. */
    private static String messageOf(FieldError error) {
        return error.isBindingFailure() ? "has an invalid value" : error.getDefaultMessage();
    }

    private static boolean isUnstorableCharacter(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataException data
                    && UNSTORABLE_CHARACTER.equals(data.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static ProblemDetail unstorableValue(HttpServletRequest request) {
        log.info("Database refused a character in a value (SQLState {})", UNSTORABLE_CHARACTER);
        return problem(
                ErrorCode.MALFORMED_REQUEST,
                "The request contains a value that cannot be stored.",
                request);
    }

    private static ProblemDetail problem(
            ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(ERROR_CODE, errorCode.name());
        return problem;
    }

    private static boolean hasErrorCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey(ERROR_CODE);
    }
}
