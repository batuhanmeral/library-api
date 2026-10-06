package com.batuhanmeral.library.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The SMS channel. Only an injection point with {@code @Qualifier("sms")} gets it; asking by type
 * alone gives the {@code @Primary} email service. Logs the SMS instead of sending it.
 */
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
