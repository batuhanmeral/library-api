package com.batuhanmeral.library.common.error;

/**
 * A service this application depends on did not answer usefully (timeout, connection error, 5xx).
 * The client may try again later; the message must not repeat the underlying error.
 */
public class ExternalServiceUnavailableException extends ApiException {

    public ExternalServiceUnavailableException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }
}
