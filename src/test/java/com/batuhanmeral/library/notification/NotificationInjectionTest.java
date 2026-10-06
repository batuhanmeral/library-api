package com.batuhanmeral.library.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Two beans implement NotificationService. Which one an injection point gets depends on how it
 * asks: by type alone it gets the {@code @Primary} one, with a qualifier the named one, and as a
 * collection all of them.
 */
@SpringBootTest
class NotificationInjectionTest {

    @Autowired private NotificationService byTypeOnly;

    @Autowired
    @Qualifier("sms")
    private NotificationService qualifiedSms;

    @Autowired private List<NotificationService> all;

    @Autowired private Map<String, NotificationService> byBeanName;

    @Autowired private ObjectProvider<NotificationService> provider;

    @Test
    void byTypeTheEmailServiceWinsBecauseItIsPrimary() {
        assertThat(byTypeOnly).isInstanceOf(EmailNotificationService.class);
        assertThat(provider.getObject()).isInstanceOf(EmailNotificationService.class);
    }

    @Test
    void theSmsQualifierSelectsTheSmsService() {
        assertThat(qualifiedSms).isInstanceOf(SmsNotificationService.class);
    }

    @Test
    void collectionsGetEveryImplementation() {
        assertThat(all).hasSize(2);
        assertThat(byBeanName)
                .containsOnlyKeys("emailNotificationService", "smsNotificationService");
    }
}
