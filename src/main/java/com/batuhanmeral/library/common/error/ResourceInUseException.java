package com.batuhanmeral.library.common.error;

/** Thrown when a resource cannot be deleted because other resources still point to it. */
public class ResourceInUseException extends ApiException {

    public ResourceInUseException(String message) {
        super(ErrorCode.RESOURCE_IN_USE, message);
    }
}
