package com.batuhanmeral.library.notification;

/**
 * Kütüphane kullanıcısına kısa mesaj gönderir. İki kanal var: e-posta ({@code @Primary}) ve SMS
 * ({@code @Qualifier("sms")}); ikisi de sadece log'lar.
 */
public interface NotificationService {

    /** Mesajı bu kanaldan gönderir; kanala ait adresi olmayan alıcı atlanır. */
    void send(Recipient recipient, String message);
}
