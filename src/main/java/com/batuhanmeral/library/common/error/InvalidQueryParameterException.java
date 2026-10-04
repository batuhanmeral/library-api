package com.batuhanmeral.library.common.error;

/**
 * Thrown when a query parameter has a value of the right type that the endpoint still cannot use,
 * such as a sort field that is not allowed or a year range that ends before it starts.
 */
public class InvalidQueryParameterException extends ApiException {

    public InvalidQueryParameterException(String message) {
        super(ErrorCode.INVALID_PARAMETER, message);
    }
}
