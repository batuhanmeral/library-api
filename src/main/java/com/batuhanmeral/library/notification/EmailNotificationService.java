package com.batuhanmeral.library.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * The default channel: an injection point that asks only for a NotificationService gets this bean,
 * because it is {@code @Primary}. Logs the email instead of sending it.
 */
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
