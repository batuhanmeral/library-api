package com.batuhanmeral.library.common.ratelimit;

import com.batuhanmeral.library.common.error.ApiException;
import com.batuhanmeral.library.common.error.ErrorCode;
import java.time.Duration;

/** The client sent more requests than its limit allows; GlobalExceptionHandler answers 429. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
        this.retryAfter = retryAfter;
    }

    /** How long until the client's window ends, for the Retry-After header. */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
