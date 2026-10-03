package com.batuhanmeral.library.common.error;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                "%s with id %s was not found.".formatted(resourceName, id));
    }
}
