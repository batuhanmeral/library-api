package com.batuhanmeral.library.common.error;

/** Başka kaynaklar hâlâ ona bağlı olduğu için silinemeyen kaynak. */
public class ResourceInUseException extends ApiException {

    public ResourceInUseException(String message) {
        super(ErrorCode.RESOURCE_IN_USE, message);
    }
}
