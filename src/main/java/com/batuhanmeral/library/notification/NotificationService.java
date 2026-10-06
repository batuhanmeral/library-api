package com.batuhanmeral.library.notification;

/**
 * Sends a short message to a library user. There are two implementations, one per channel:
 * EmailNotificationService ({@code @Primary}, used wherever no channel is asked for) and
 * SmsNotificationService ({@code @Qualifier("sms")}). Neither talks to a real mail or SMS provider;
 * they only log the message.
 */
public interface NotificationService {

    /**
     * Sends the message over this service's channel. A recipient without an address for the channel
     * (no phone for SMS) is skipped.
     */
    void send(Recipient recipient, String message);
}
