package com.batuhanmeral.library.notification;

/** Mesajın gidebileceği adresler; her kanal ihtiyacı olanı kullanır. */
public record Recipient(String email, String phone) {}
