package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.loan.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * What the controller calls to lend and return books. Each call goes to LoanService, whose methods
 * are transactions of their own. When another request changed the same book between our read and
 * our commit (Book.version), the transaction is rolled back and the call is made again: a new
 * transaction reads the book again and either finds a copy or fails with the right rule
 * (BOOK_NOT_AVAILABLE, LOAN_ALREADY_RETURNED).
 *
 * <p>The retry must wrap the transaction, not run inside it: inside, the persistence context still
 * holds the old book and the transaction is already marked for rollback. That is why it lives in
 * this separate bean instead of on LoanService's {@code @Transactional} methods, where the order of
 * the two proxies would decide whether a retry gets a new transaction.
 *
 * <p>Delays: 50 ms, then 100, 200 and 400 ms (multiplier 2), each moved by up to 25 ms at random
 * (jitter), so requests that lost together do not retry in the same instant again.
 */
@Service
@RequiredArgsConstructor
public class LoanFacade {

    /** Retries after the first attempt; five calls in all. */
    static final long MAX_RETRIES = 4;

    private final LoanService loanService;

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = MAX_RETRIES,
            delay = 50,
            multiplier = 2,
            jitter = 25,
            maxDelay = 500)
    public LoanResponse borrow(LoanRequest request) {
        return loanService.borrow(request);
    }

    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = MAX_RETRIES,
            delay = 50,
            multiplier = 2,
            jitter = 25,
            maxDelay = 500)
    public LoanResponse returnBook(Long loanId) {
        return loanService.returnBook(loanId);
    }
}
