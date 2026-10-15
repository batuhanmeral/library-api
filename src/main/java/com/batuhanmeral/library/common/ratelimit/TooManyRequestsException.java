package com.batuhanmeral.library.common.ratelimit;

import com.batuhanmeral.library.common.error.ApiException;
import com.batuhanmeral.library.common.error.ErrorCode;
import java.time.Duration;

/** İstemci sınırını aştı; GlobalExceptionHandler 429 döner. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
        this.retryAfter = retryAfter;
    }

    /** Pencerenin bitmesine kalan süre, Retry-After için. */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
