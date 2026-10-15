package com.batuhanmeral.library.common.error;

/** İstek gövdesindeki geçersiz bir alan; 400 cevabında {@code errors} altında listelenir. */
public record FieldViolation(String field, String message) {}
