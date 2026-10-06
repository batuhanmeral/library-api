package com.batuhanmeral.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** The copy counts change only through these methods; Book has no setters for them. */
class BookTest {

    @Test
    void borrowingAndReturningMoveTheAvailableCopies() {
        Book book = new Book("Dune", 2);

        book.borrowCopy();
        book.borrowCopy();

        assertThat(book.getAvailableCopies()).isZero();
        book.returnCopy();
        assertThat(book.getAvailableCopies()).isEqualTo(1);
        assertThat(book.getTotalCopies()).isEqualTo(2);
    }

    @Test
    void aBookWithNoAvailableCopyCannotBeBorrowed() {
        Book book = new Book("Dune", 1);
        book.borrowCopy();

        assertThatThrownBy(book::borrowCopy)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_NOT_AVAILABLE);
    }

    @Test
    void aCopyCannotBeReturnedWhenNoneIsOnLoan() {
        Book book = new Book("Dune", 1);

        assertThatThrownBy(book::returnCopy).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changingTheTotalKeepsTheCopiesOnLoanOnLoan() {
        Book book = new Book("Dune", 3);
        book.borrowCopy();

        book.changeTotalCopies(5);

        assertThat(book.getTotalCopies()).isEqualTo(5);
        assertThat(book.getAvailableCopies()).isEqualTo(4);
    }

    @Test
    void theTotalCanDropToTheNumberOfCopiesOnLoanButNotBelow() {
        Book book = new Book("Dune", 3);
        book.borrowCopy();
        book.borrowCopy();

        book.changeTotalCopies(2);
        assertThat(book.getAvailableCopies()).isZero();

        assertThatThrownBy(() -> book.changeTotalCopies(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("2 copies of this book are on loan; totalCopies cannot be lower.")
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.COPIES_ON_LOAN);
        assertThat(book.getTotalCopies()).isEqualTo(2);
    }
}
