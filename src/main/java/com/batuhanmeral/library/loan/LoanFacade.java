package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.loan.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Controller ödünç ve iadeyi buradan çağırır. Başka istek aynı kitabı değiştirdiyse (Book.version)
 * transaction geri alınır ve çağrı yeni bir transaction'la tekrarlanır.
 *
 * <p>Retry transaction'ı sarmalı, içinde çalışmamalı; bu yüzden ayrı bean. Bekleme 50 ms'den
 * başlayıp ikiye katlanır, jitter aynı anda kaybedenlerin aynı anda denemesini önler.
 */
@Service
@RequiredArgsConstructor
public class LoanFacade {

    /** İlk denemeden sonraki tekrarlar; toplam beş çağrı. */
    static final long MAX_RETRIES = 4;

    private final LoanService loanService;

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = MAX_RETRIES,
            delay = 50,
            multiplier = 2,
            jitter = 25,
            maxDelay = 500)
    public LoanResponse borrow(Long memberId, LoanRequest request) {
        return loanService.borrow(memberId, request);
    }

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = MAX_RETRIES,
            delay = 50,
            multiplier = 2,
            jitter = 25,
            maxDelay = 500)
    public LoanResponse returnBook(Long loanId, AuthenticatedUser caller) {
        return loanService.returnBook(loanId, caller);
    }
}
