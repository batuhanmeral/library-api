package com.batuhanmeral.library.common.error;

/**
 * The caller is logged in but may not act on this resource, such as a member reading another
 * member's loan. Rules that need the resource itself live in the service; rules that depend only on
 * the path and the role are in SecurityConfig.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
