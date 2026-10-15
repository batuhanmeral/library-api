package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.common.audit.BaseEntity;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import com.batuhanmeral.library.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bir üyeye verilen bir kopya. İlişkiler sadece bu tarafta; setter yok, durum sadece {@link
 * #markOverdue} ve {@link #markReturned} ile değişir.
 */
@Entity
@Table(name = "loans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Loan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    @Column(nullable = false)
    private LocalDate loanDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    /** {@code loanDate}'ten {@code periodDays} gün sonra dolan yeni aktif ödünç. */
    public Loan(Book book, User member, LocalDate loanDate, int periodDays) {
        this.book = book;
        this.member = member;
        this.loanDate = loanDate;
        this.dueDate = loanDate.plusDays(periodDays);
        this.status = LoanStatus.ACTIVE;
    }

    /**
     * Gece işi süresi geçmiş aktif ödünç için çağırır. Başka durum çağıranın sorgusunda bir
     * hatadır, bu yüzden IllegalStateException.
     */
    public void markOverdue(LocalDate today) {
        if (status != LoanStatus.ACTIVE || !dueDate.isBefore(today)) {
            throw new IllegalStateException(
                    "Loan " + id + " is " + status + ", due " + dueDate + "; not overdue.");
        }
        this.status = LoanStatus.OVERDUE;
    }

    /** Ödüncü kapatır; kopyayı kitaba çağıran geri koyar. */
    public void markReturned(LocalDate returnDate) {
        if (status == LoanStatus.RETURNED) {
            throw new BusinessRuleException(
                    ErrorCode.LOAN_ALREADY_RETURNED, "This loan was already returned.");
        }
        this.status = LoanStatus.RETURNED;
        this.returnDate = returnDate;
    }
}
