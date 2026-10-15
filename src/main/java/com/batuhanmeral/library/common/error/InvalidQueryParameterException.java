package com.batuhanmeral.library.common.error;

/**
 * Tipi doğru ama uç noktanın kullanamadığı parametre, örn. izin verilmeyen sıralama alanı ya da
 * ters yıl aralığı.
 */
public class InvalidQueryParameterException extends ApiException {

    public InvalidQueryParameterException(String message) {
        super(ErrorCode.INVALID_PARAMETER, message);
    }
}
