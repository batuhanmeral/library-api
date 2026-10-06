package com.batuhanmeral.library.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Neither service sends anything; they write a log line. The line must not hold the full address or
 * phone number (personal data in logs) and must stay one line whatever the text holds.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationServicesTest {

    private static final Recipient ADA = new Recipient("ada@example.com", "+15555550142");

    @Test
    void anEmailIsLoggedWithAMaskedAddress(CapturedOutput output) {
        new EmailNotificationService().send(ADA, "You borrowed \"Dune\".");

        assertThat(output).contains("Email to a***@example.com: You borrowed \"Dune\".");
        assertThat(output).doesNotContain("ada@example.com");
    }

    @Test
    void anSmsIsLoggedWithAMaskedNumber(CapturedOutput output) {
        new SmsNotificationService().send(ADA, "Reminder");

        assertThat(output).contains("SMS to ***42: Reminder");
        assertThat(output).doesNotContain("5555550142");
    }

    @Test
    void aMessageCannotAddLinesToTheLog(CapturedOutput output) {
        new EmailNotificationService().send(ADA, "Dune\nERROR forged line");

        assertThat(output).contains("Email to a***@example.com: Dune ERROR forged line");
    }

    @Test
    void aRecipientWithoutAnAddressForTheChannelIsSkipped(CapturedOutput output) {
        new SmsNotificationService().send(new Recipient("ada@example.com", null), "Reminder");

        assertThat(output).doesNotContain("SMS to");
    }
}
