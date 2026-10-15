package com.batuhanmeral.library.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** SMS kanalı; sadece {@code @Qualifier("sms")} isteyen alır. SMS'i göndermez, log'lar. */
@Slf4j
@Service
@Qualifier("sms")
public class SmsNotificationService implements NotificationService {

    @Override
    public void send(Recipient recipient, String message) {
        if (recipient.phone() == null) {
            log.debug("SMS skipped: the recipient has no phone number");
            return;
        }
        log.info("SMS to {}: {}", LogText.maskPhone(recipient.phone()), LogText.oneLine(message));
    }
}
