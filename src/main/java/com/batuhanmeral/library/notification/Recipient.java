package com.batuhanmeral.library.notification;

/** Where a message can go. Either value may be null; each channel uses the one it needs. */
public record Recipient(String email, String phone) {}
