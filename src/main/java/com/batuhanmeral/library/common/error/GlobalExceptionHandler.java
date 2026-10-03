package com.batuhanmeral.library.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        return problem(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // The database message names tables and repeats the rejected values, which may hold line
        // breaks. Neither the client nor the log gets it; the constraint name is enough to debug.
        String constraint =
                ex.getCause() instanceof ConstraintViolationException violation
                        ? violation.getConstraintName()
                        : "unknown";
        log.warn("Database constraint violated: {}", constraint);
        return problem(
                ErrorCode.DATA_CONFLICT, "The request conflicts with existing data.", request);
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
                        .map(error ->
                                new FieldViolation(error.getField(), error.getDefaultMessage()))
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
