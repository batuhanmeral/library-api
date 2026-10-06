package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.notification.NotificationService;
import com.batuhanmeral.library.notification.Recipient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the confirmation emails for loans, once the loan is committed. LoanService publishes the
 * event inside its transaction; a {@code @TransactionalEventListener} is not called then but
 * registered with the transaction and called in the chosen phase. AFTER_COMMIT: a borrow that is
 * rolled back, for example one that lost a race on the last copy, sends no email; a retried borrow
 * sends one, for the attempt that committed.
 *
 * <p>The listener runs in the request's thread after the commit. The loan is saved by then: an
 * exception here is logged by Spring and does not change the response. Any database work here would
 * need a transaction of its own (REQUIRES_NEW); the committed one cannot write any more.
 */
@Component
@RequiredArgsConstructor
class LoanNotificationListener {

    /** No qualifier: the {@code @Primary} implementation, email. */
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
