package com.batuhanmeral.library.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * NotificationService'i iki bean implement eder: sadece tiple istenince {@code @Primary}, qualifier
 * ile adı verilen, koleksiyon olarak hepsi gelir.
 */
@SpringBootTest
class NotificationInjectionTest implements DatabaseTest {

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
