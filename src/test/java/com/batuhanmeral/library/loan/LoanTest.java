package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanTest {

    private static final LocalDate LOAN_DATE = LocalDate.of(2026, 11, 1);

    @Test
    void aLoanPastItsDueDateCanBeMarkedOverdue() {
        Loan loan = newLoan();

        loan.markOverdue(LocalDate.of(2026, 11, 16));

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
    }

    @Test
    void aLoanDueTodayIsNotOverdueYet() {
        Loan loan = newLoan();

        assertThatThrownBy(() -> loan.markOverdue(LocalDate.of(2026, 11, 15)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void onlyAnActiveLoanCanBecomeOverdue() {
        Loan loan = newLoan();
        loan.markReturned(LocalDate.of(2026, 11, 20));

        assertThatThrownBy(() -> loan.markOverdue(LocalDate.of(2026, 11, 21)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
    }

    @Test
    void anOverdueLoanCanStillBeReturned() {
        Loan loan = newLoan();
        loan.markOverdue(LocalDate.of(2026, 11, 16));

        loan.markReturned(LocalDate.of(2026, 11, 17));

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(loan.getReturnDate()).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    private static Loan newLoan() {
        User member = new User("ada@example.com", "{noop}x", "Ada", Role.MEMBER);
        return new Loan(new Book("Dune", 1), member, LOAN_DATE, 14);
    }
}
