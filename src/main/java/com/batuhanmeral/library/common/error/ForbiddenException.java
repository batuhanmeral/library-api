package com.batuhanmeral.library.common.error;

/**
 * Giriş yapılmış ama bu kaynak üzerinde yetki yok, örn. başka üyenin ödüncünü okumak. Kaynağa bakan
 * kurallar service'te, sadece yol ve role bakanlar SecurityConfig'te.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
