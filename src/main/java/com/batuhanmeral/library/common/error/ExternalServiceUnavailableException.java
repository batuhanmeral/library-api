package com.batuhanmeral.library.common.error;

/**
 * Bağlı olunan servis işe yarar cevap vermedi (timeout, bağlantı hatası, 5xx). Mesaj alttaki hatayı
 * tekrarlamamalı.
 */
public class ExternalServiceUnavailableException extends ApiException {

    public ExternalServiceUnavailableException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }
}
