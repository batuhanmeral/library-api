package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import com.batuhanmeral.library.common.error.ForbiddenException;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import com.batuhanmeral.library.config.CacheNames;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.loan.dto.LoanResponse;
import com.batuhanmeral.library.loan.dto.LoanSearchCriteria;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lending and returning. Each public method is one transaction: everything it changes is saved
 * together or, when it throws a RuntimeException, not at all. Both change the book's available
 * copies, so both drop the book's cache entry (GET /books/{id}). The controller does not call
 * borrow and returnBook directly but through LoanFacade, which runs them again when they lose a
 * race.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanService {

    /** Loans that hold a copy: they count towards the limit and block deleting the book. */
    public static final Set<LoanStatus> OPEN_STATUSES =
            EnumSet.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final LoanMapper loanMapper;
    private final LoanProperties loanProperties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * Lends one copy of a book. Two kinds of races are possible, and each has its own guard:
     *
     * <ul>
     *   <li>Two members, the same book: the book is not locked; a request that changes it between
     *       our read and our commit makes this commit fail (Book.version), so two members cannot
     *       both take the last copy.
     *   <li>One member, two books at once: the books differ, so their versions do not conflict. The
     *       member's row is locked instead, so the second request waits until the first has
     *       committed and then counts its loan. The same lock keeps one member from taking two
     *       copies of one book with two requests at once.
     * </ul>
     *
     * <p>The member comes from the token. SecurityConfig lets only MEMBER tokens in, but a token is
     * not checked against the database: one issued before the user's role changed is still
     * accepted, so the role is checked here again, as the database has it now.
     */
    @CacheEvict(cacheNames = CacheNames.BOOKS, key = "#request.bookId()")
    @Transactional
    public LoanResponse borrow(Long memberId, LoanRequest request) {
        User member =
                userRepository
                        .findLockedById(memberId)
                        .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        if (member.getRole() != Role.MEMBER) {
            throw new BusinessRuleException(
                    ErrorCode.NOT_A_MEMBER, "Only members can borrow books.");
        }
        Book book =
                bookRepository
                        .findById(request.bookId())
                        .orElseThrow(() -> new ResourceNotFoundException("Book", request.bookId()));
        LocalDate today = LocalDate.now(clock);

        if (loanRepository.hasOverdueLoan(member.getId(), today)) {
            throw new BusinessRuleException(
                    ErrorCode.MEMBER_HAS_OVERDUE_LOAN,
                    "The member has an overdue loan and cannot borrow until it is returned.");
        }
        if (loanRepository.existsByMemberIdAndBookIdAndStatusIn(
                member.getId(), book.getId(), OPEN_STATUSES)) {
            throw new BusinessRuleException(
                    ErrorCode.BOOK_ALREADY_BORROWED, "The member already has this book on loan.");
        }
        long openLoans = loanRepository.countByMemberIdAndStatusIn(member.getId(), OPEN_STATUSES);
        if (openLoans >= loanProperties.maxActiveLoans()) {
            throw new BusinessRuleException(
                    ErrorCode.LOAN_LIMIT_REACHED,
                    "The member already has %d active loans, the most allowed."
                            .formatted(openLoans));
        }
        book.borrowCopy();

        Loan loan = loanRepository.save(new Loan(book, member, today, loanProperties.periodDays()));
        // Listeners decide when to act: the confirmation email waits for the commit, so a borrow
        // that is rolled back (or loses a race and is retried) announces nothing.
        events.publishEvent(
                new LoanBorrowedEvent(
                        loan.getId(), member.getEmail(), book.getTitle(), loan.getDueDate()));
        return loanMapper.toResponse(loan);
    }

    /**
     * The member who borrowed the book, or an admin (a book handed in at the desk). The book's
     * cache entry is dropped after the commit; its id comes from the result (#result), because the
     * request names only the loan.
     */
    @CacheEvict(cacheNames = CacheNames.BOOKS, key = "#result.book().id()")
    @Transactional
    public LoanResponse returnBook(Long loanId, AuthenticatedUser caller) {
        Loan loan = getLoan(loanId);
        checkAccess(loan, caller);
        loan.markReturned(LocalDate.now(clock));
        loan.getBook().returnCopy();
        // Write now: the response shows the new updatedAt, and a conflicting change of the book
        // fails here, inside this method.
        loanRepository.flush();
        events.publishEvent(
                new LoanReturnedEvent(
                        loan.getId(),
                        loan.getMember().getEmail(),
                        loan.getBook().getTitle(),
                        loan.getReturnDate()));
        return loanMapper.toResponse(loan);
    }

    public LoanResponse findById(Long id, AuthenticatedUser caller) {
        Loan loan = getLoan(id);
        checkAccess(loan, caller);
        return loanMapper.toResponse(loan);
    }

    public Page<LoanResponse> search(LoanSearchCriteria criteria, Pageable pageable) {
        return loanRepository
                .findAll(LoanSpecifications.matching(criteria), pageable)
                .map(loanMapper::toResponse);
    }

    /**
     * A member may see and return only their own loans; an admin any loan. This rule needs the
     * loan's member, which only the database knows, so it lives here and not in SecurityConfig. 403
     * rather than 404 tells the caller that the loan exists; ids are not secret here.
     */
    private static void checkAccess(Loan loan, AuthenticatedUser caller) {
        if (!caller.isAdmin() && !loan.getMember().getId().equals(caller.id())) {
            throw new ForbiddenException("This loan belongs to another member.");
        }
    }

    private Loan getLoan(Long id) {
        return loanRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", id));
    }
}
