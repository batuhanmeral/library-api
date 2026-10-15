package com.batuhanmeral.library.common.error;

/**
 * Uygulamanın bilerek fırlattığı hataların tabanı. Mesaj cevabın {@code detail}'i olur, istemciye
 * gösterilebilir olmalı.
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
