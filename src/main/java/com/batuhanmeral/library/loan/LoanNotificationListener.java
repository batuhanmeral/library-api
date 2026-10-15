package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.notification.NotificationService;
import com.batuhanmeral.library.notification.Recipient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ödünç e-postalarını commit'ten sonra gönderir (AFTER_COMMIT): geri alınan ödünç e-posta
 * göndermez. Buradaki hata log'lanır, cevabı değiştirmez.
 */
@Component
@RequiredArgsConstructor
class LoanNotificationListener {

    /** Qualifier yok: {@code @Primary} olan e-posta. */
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBorrowed(LoanBorrowedEvent event) {
        notificationService.send(
                new Recipient(event.memberEmail(), null),
                "You borrowed \"%s\" (loan %d). Please return it by %s."
                        .formatted(event.bookTitle(), event.loanId(), event.dueDate()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onReturned(LoanReturnedEvent event) {
        notificationService.send(
                new Recipient(event.memberEmail(), null),
                "You returned \"%s\" (loan %d) on %s. Thank you."
                        .formatted(event.bookTitle(), event.loanId(), event.returnDate()));
    }
}
