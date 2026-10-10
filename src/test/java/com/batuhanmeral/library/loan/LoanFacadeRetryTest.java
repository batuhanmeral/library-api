package com.batuhanmeral.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import com.batuhanmeral.library.config.ResilienceConfig;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.loan.dto.LoanResponse;
import com.batuhanmeral.library.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * The retry rules of LoanFacade in a context of two beans: the facade and the configuration that
 * turns on {@code @Retryable}. LoanService is a mock, so each "attempt" is one call to it.
 */
@SpringJUnitConfig({ResilienceConfig.class, LoanFacade.class})
class LoanFacadeRetryTest {

    private static final LoanRequest REQUEST = new LoanRequest(1L);

    private static final long MEMBER_ID = 2L;

    private static final AuthenticatedUser CALLER = new AuthenticatedUser(MEMBER_ID, Role.MEMBER);

    @Autowired private LoanFacade loanFacade;

    @MockitoBean private LoanService loanService;

    @Test
    void aConflictIsRetriedAndTheNextAttemptsResultIsReturned() {
        LoanResponse borrowed = response();
        when(loanService.borrow(MEMBER_ID, REQUEST)).thenThrow(conflict()).thenReturn(borrowed);

        assertThat(loanFacade.borrow(MEMBER_ID, REQUEST)).isSameAs(borrowed);

        verify(loanService, times(2)).borrow(MEMBER_ID, REQUEST);
    }

    @Test
    void afterTheLastRetryTheConflictReachesTheCaller() {
        when(loanService.borrow(MEMBER_ID, REQUEST)).thenThrow(conflict());

        assertThatThrownBy(() -> loanFacade.borrow(MEMBER_ID, REQUEST))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(loanService, times((int) LoanFacade.MAX_RETRIES + 1)).borrow(MEMBER_ID, REQUEST);
    }

    @Test
    void aBrokenRuleIsNotRetried() {
        when(loanService.borrow(MEMBER_ID, REQUEST))
                .thenThrow(new BusinessRuleException(ErrorCode.BOOK_NOT_AVAILABLE, "No copy."));

        assertThatThrownBy(() -> loanFacade.borrow(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessRuleException.class);

        verify(loanService, times(1)).borrow(MEMBER_ID, REQUEST);
    }

    @Test
    void aReturnIsRetriedToo() {
        LoanResponse returned = response();
        when(loanService.returnBook(7L, CALLER)).thenThrow(conflict()).thenReturn(returned);

        assertThat(loanFacade.returnBook(7L, CALLER)).isSameAs(returned);

        verify(loanService, times(2)).returnBook(7L, CALLER);
    }

    private static OptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException(Book.class, 1L);
    }

    private static LoanResponse response() {
        return new LoanResponse(1L, null, null, null, null, null, LoanStatus.ACTIVE, null, null);
    }
}
