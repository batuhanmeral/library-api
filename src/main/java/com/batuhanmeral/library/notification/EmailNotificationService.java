package com.batuhanmeral.library.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Varsayılan kanal ({@code @Primary}). E-postayı göndermez, log'lar. */
@Slf4j
@Service
@Primary
public class EmailNotificationService implements NotificationService {

    @Override
    public void send(Recipient recipient, String message) {
        if (recipient.email() == null) {
            log.debug("Email skipped: the recipient has no email address");
            return;
        }
        log.info("Email to {}: {}", LogText.maskEmail(recipient.email()), LogText.oneLine(message));
    }
}
