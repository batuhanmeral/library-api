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
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception that leaves a controller into an RFC 9457 problem response with an {@code
 * errorCode} field. The parent class already handles the exceptions Spring MVC raises (unreadable
 * body, wrong method, unknown path, ...); the methods here add the error code and cover the
 * application's own exceptions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_CODE = "errorCode";

    /**
     * Database constraints that guard a business rule the service also checks. When the database is
     * the one to refuse (the service check was missed), the client gets the rule's own code, as if
     * the service had refused. Any other violation is a DATA_CONFLICT.
     */
    private static final Map<String, RuleViolation> RULE_CONSTRAINTS =
            Map.of(
                    "uk_loans_open_member_book",
                    new RuleViolation(
                            ErrorCode.BOOK_ALREADY_BORROWED,
                            "The member already has this book on loan."));

    private record RuleViolation(ErrorCode errorCode, String detail) {}

    /**
     * SQLState 22021 (character_not_in_repertoire): PostgreSQL refuses a NUL character (U+0000) in
     * any text value, stored or compared. The client sent it, so it is a bad request, not a
     * conflict and not a server error. Other SQLState class 22 errors (a value too long, a bad
     * cast) mean a check is missing in the application and keep their own handling.
     */
    private static final String UNSTORABLE_CHARACTER = "22021";

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        return problem(ex.getErrorCode(), ex.getMessage(), request);
    }

    /**
     * 429 with Retry-After (RFC 9110): the seconds until the client's window ends, rounded up, so a
     * client that waits that long is never refused again for the same window.
     */
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
        // The database message names tables and repeats the rejected values, which may hold line
        // breaks. Neither the client nor the log gets it; the constraint name is enough to debug.
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
     * Another transaction changed the same row between our read and our write (see Book.version).
     * The client can reload and try again; nothing of this request was saved.
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
     * 401: no token where one is needed, a bad token, or a failed login. RFC 9110 asks a 401 to
     * name the authentication scheme in WWW-Authenticate; RFC 6750 adds the error for a bad token.
     * Most of these come from Spring Security's filters through SecurityErrorHandler; a failed
     * login comes from AuthService.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        if (ex instanceof InternalAuthenticationServiceException && isUnstorableCharacter(ex)) {
            // Spring Security wraps every failure of the user lookup, this one too.
            return ResponseEntity.badRequest().body(unstorableValue(request));
        }
        if (ex instanceof InternalAuthenticationServiceException) {
            // Not the client's credentials: the account could not be loaded (the database is
            // down, for example). A 401 would tell the client to log in and hide the outage.
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

    /** 403: a valid token whose user lacks the role a rule of SecurityConfig asks for. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(ErrorCode.ACCESS_DENIED, "You do not have permission to do this.", request);
    }

    /**
     * 400: Spring Security's firewall (StrictHttpFirewall) refused the request before anything else
     * read it: a ";" or an encoded "/" in the path, "../", a header with control characters. The
     * rejected path is left out of the answer and the log: it is what the firewall did not trust,
     * and URI.create could not even parse some of them ("%zz").
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
     * Every response built by the parent class passes through here. Most of its handlers pass a
     * null body; the problem then comes from the exception itself.
     */
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

    /**
     * A binding failure is a value that could not be converted, such as "LOST" for a loan status.
     * Spring's message for it names the Java type and repeats the value; a fixed text is enough.
     */
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
