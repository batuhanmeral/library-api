package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.notification.NotificationService;
import com.batuhanmeral.library.notification.Recipient;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Marks active loans past their due date as OVERDUE and texts each member concerned once. Run by
 * OverdueLoanScheduler every night and by POST /admin/jobs/overdue-check.
 *
 * <ul>
 *   <li>Batches: members are handled {@code batch-size} at a time, each batch in its own
 *       transaction (TransactionTemplate). No transaction holds locks on thousands of rows, and
 *       when a batch fails, the batches before it stay committed; the run stops and the next run
 *       goes on from there.
 *   <li>The SMS for a batch goes out after its transaction has committed; a rolled back batch sends
 *       nothing.
 *   <li>Running it again is harmless: it only looks at ACTIVE loans, and the ones it handled are
 *       OVERDUE now.
 *   <li>One run at a time per application instance: {@link #tryClaim()} is the shared guard of the
 *       scheduler and the endpoint. Several instances need a lock in the database (ShedLock).
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueLoanJob {

    private final AtomicBoolean running = new AtomicBoolean();

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final LoanProperties loanProperties;
    private final Clock clock;

    /** Reminders go out by SMS: the qualifier picks the SMS bean over the {@code @Primary} one. */
    @Qualifier("sms")
    private final NotificationService smsNotificationService;

    /** Claims the job for the caller; false while another run holds it. */
    boolean tryClaim() {
        return running.compareAndSet(false, true);
    }

    /** Gives the claim back without running, for a caller that could not start the run. */
    void release() {
        running.set(false);
    }

    boolean isRunning() {
        return running.get();
    }

    /** Claims the job and runs it; for callers that do not need to start it elsewhere. */
    public OverdueCheckResult run() {
        if (!tryClaim()) {
            throw new IllegalStateException("The overdue check is already running.");
        }
        return runClaimed();
    }

    /**
     * Runs the job after a successful {@link #tryClaim()} and releases the claim at the end. Only
     * the scheduler, the runner and this class call it: a call without the claim would release
     * someone else's.
     */
    OverdueCheckResult runClaimed() {
        try {
            LocalDate today = LocalDate.now(clock);
            int loansMarked = 0;
            int remindersSent = 0;
            Batch batch;
            // The run ends when no member is left, not when a batch marked nothing: the loans of
            // a whole batch may have been returned between its two queries.
            while ((batch = markNextBatch(today)).members() > 0) {
                for (Reminder reminder : batch.reminders()) {
                    loansMarked += reminder.loanCount();
                    if (sendReminder(reminder)) {
                        remindersSent++;
                    }
                }
            }
            log.info(
                    "Overdue check for {}: {} loans marked overdue, {} reminders sent",
                    today,
                    loansMarked,
                    remindersSent);
            return new OverdueCheckResult(loansMarked, remindersSent);
        } finally {
            release();
        }
    }

    /** One transaction: the next members with loans past due, their loans marked overdue. */
    private Batch markNextBatch(LocalDate today) {
        return transactionTemplate.execute(status -> {
            List<Long> memberIds =
                    loanRepository.findMemberIdsWithLoansDueBefore(
                            today, Limit.of(loanProperties.overdueCheck().batchSize()));
            if (memberIds.isEmpty()) {
                return new Batch(0, List.of());
            }
            // One query for the members; the loans' member proxies then resolve from the
            // persistence context instead of one query each.
            Map<Long, User> members =
                    userRepository.findAllById(memberIds).stream()
                            .collect(Collectors.toMap(User::getId, Function.identity()));
            Map<Long, List<Loan>> loansByMember =
                    loanRepository
                            .findLockedByMemberIdInAndStatusAndDueDateBefore(
                                    memberIds, LoanStatus.ACTIVE, today)
                            .stream()
                            .collect(Collectors.groupingBy(loan -> loan.getMember().getId()));
            loansByMember.values().forEach(loans -> loans.forEach(l -> l.markOverdue(today)));
            List<Reminder> reminders =
                    loansByMember.entrySet().stream()
                            .map(entry ->
                                    Reminder.of(members.get(entry.getKey()), entry.getValue()))
                            .toList();
            return new Batch(memberIds.size(), reminders);
        });
    }

    private boolean sendReminder(Reminder reminder) {
        if (reminder.phone() == null) {
            return false;
        }
        try {
            smsNotificationService.send(
                    new Recipient(reminder.email(), reminder.phone()),
                    "Library reminder: you have %d overdue loan(s). The first was due on %s."
                            .formatted(reminder.loanCount(), reminder.firstDueDate()));
            return true;
        } catch (RuntimeException e) {
            // The loans stay overdue; one failed message must not stop the reminders for others.
            log.warn("Overdue reminder could not be sent: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** The members a batch looked at, and the reminders for those whose loans it marked. */
    private record Batch(int members, List<Reminder> reminders) {}

    private record Reminder(String email, String phone, int loanCount, LocalDate firstDueDate) {

        static Reminder of(User member, List<Loan> loans) {
            LocalDate first =
                    loans.stream()
                            .map(Loan::getDueDate)
                            .min(Comparator.naturalOrder())
                            .orElseThrow();
            return new Reminder(member.getEmail(), member.getPhone(), loans.size(), first);
        }
    }
}
