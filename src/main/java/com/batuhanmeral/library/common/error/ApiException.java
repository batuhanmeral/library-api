package com.batuhanmeral.library.common.error;

/**
 * Base class for errors the application raises on purpose. The message becomes the {@code detail}
 * of the error response, so it must be safe to show to a client.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
