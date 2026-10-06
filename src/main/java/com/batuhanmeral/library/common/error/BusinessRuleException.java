package com.batuhanmeral.library.common.error;

/**
 * Thrown when a request is valid on its own but breaks a rule of the library, such as borrowing a
 * book with no copy left. The error code tells the rule apart; one class serves all of them.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
