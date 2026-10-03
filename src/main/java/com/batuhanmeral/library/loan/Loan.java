package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.common.audit.BaseEntity;
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
import lombok.Setter;

/**
 * One copy of a book lent to a member. The relationships exist only on this side: neither Book nor
 * User has a list of loans; loans are found with repository queries (step 6).
 */
@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
public class Loan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
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

    /** A new active loan, due {@code periodDays} days after {@code loanDate}. */
    public Loan(Book book, User member, LocalDate loanDate, int periodDays) {
        this.book = book;
        this.member = member;
        this.loanDate = loanDate;
        this.dueDate = loanDate.plusDays(periodDays);
        this.status = LoanStatus.ACTIVE;
    }
}
