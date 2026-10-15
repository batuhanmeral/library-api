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
 * Süresi geçmiş aktif ödünçleri OVERDUE yapar ve her üyeye bir SMS atar. Her gece
 * OverdueLoanScheduler ve POST /admin/jobs/overdue-check çalıştırır.
 *
 * <p>Üyeler {@code batch-size} kadarlık gruplar halinde, her grup kendi transaction'ında işlenir;
 * SMS commit'ten sonra gider. Tekrar çalıştırmak zararsızdır. Uygulama başına aynı anda tek çalışma
 * ({@link #tryClaim()}).
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

    /** Hatırlatmalar SMS ile gider: qualifier {@code @Primary} yerine SMS bean'ini seçer. */
    @Qualifier("sms")
    private final NotificationService smsNotificationService;

    /** İşi çağıran için alır; başka çalışma varken false. */
    boolean tryClaim() {
        return running.compareAndSet(false, true);
    }

    /** Başlatamayan çağıran için hakkı geri verir. */
    void release() {
        running.set(false);
    }

    boolean isRunning() {
        return running.get();
    }

    /** Hakkı alıp işi çalıştırır. */
    public OverdueCheckResult run() {
        if (!tryClaim()) {
            throw new IllegalStateException("The overdue check is already running.");
        }
        return runClaimed();
    }

    /**
     * Başarılı {@link #tryClaim()} sonrası çalışır ve sonunda hakkı bırakır; hak almadan çağırmak
     * başkasınınkini bırakırdı.
     */
    OverdueCheckResult runClaimed() {
        try {
            LocalDate today = LocalDate.now(clock);
            int loansMarked = 0;
            int remindersSent = 0;
            Batch batch;
            // Grup hiçbir şey işaretlemediğinde değil, üye kalmadığında biter; iki sorgu arasında
            // ödünçler iade edilmiş olabilir.
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

    /** Tek transaction: süresi geçmiş sonraki üyeler, ödünçleri OVERDUE olarak. */
    private Batch markNextBatch(LocalDate today) {
        return transactionTemplate.execute(status -> {
            List<Long> memberIds =
                    loanRepository.findMemberIdsWithLoansDueBefore(
                            today, Limit.of(loanProperties.overdueCheck().batchSize()));
            if (memberIds.isEmpty()) {
                return new Batch(0, List.of());
            }
            // Üyeler için tek sorgu; ödünçlerin üye proxy'leri persistence context'ten çözülür.
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
            // Ödünçler gecikmiş kalır; bir mesajın hatası diğerlerini durdurmamalı.
            log.warn("Overdue reminder could not be sent: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Grubun baktığı üye sayısı ve işaretlenenler için hatırlatmalar. */
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
