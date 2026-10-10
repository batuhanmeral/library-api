package com.batuhanmeral.library.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Machine-readable error codes sent in the {@code errorCode} field of every error response. Clients
 * branch on these codes; the human-readable {@code detail} text may change.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    RESOURCE_IN_USE(HttpStatus.CONFLICT),
    DATA_CONFLICT(HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    BOOK_NOT_AVAILABLE(HttpStatus.CONFLICT),
    COPIES_ON_LOAN(HttpStatus.CONFLICT),
    LOAN_ALREADY_RETURNED(HttpStatus.CONFLICT),
    BOOK_ALREADY_BORROWED(HttpStatus.CONFLICT),
    JOB_ALREADY_RUNNING(HttpStatus.CONFLICT),
    LOAN_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT),
    MEMBER_HAS_OVERDUE_LOAN(HttpStatus.UNPROCESSABLE_CONTENT),
    NOT_A_MEMBER(HttpStatus.UNPROCESSABLE_CONTENT),
    ISBN_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST),
    /** No token where one is needed. */
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    /** A token was sent but is malformed, forged or expired. */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    /** Login with a wrong password or an address that has no account. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    /** A valid token whose user may not do this. */
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** Picks a code for errors raised by Spring MVC itself, based on the response status. */
    static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> AUTHENTICATION_REQUIRED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? INTERNAL_ERROR : REQUEST_REJECTED;
        };
    }
}
