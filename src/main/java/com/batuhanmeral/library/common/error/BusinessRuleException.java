package com.batuhanmeral.library.common.error;

/**
 * Kendi başına geçerli ama kütüphane kuralını bozan istek, örn. kopyası kalmamış kitabı ödünç
 * almak. Kuralı hata kodu ayırt eder.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
