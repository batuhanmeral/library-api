package com.batuhanmeral.library.common.error;

/** One invalid field in a request body, listed under {@code errors} in a 400 response. */
public record FieldViolation(String field, String message) {}
