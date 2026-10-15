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
 * Ödünç ve iade. Her public metod tek transaction'dır; ikisi de kitabın cache kaydını siler.
 * Controller bunları yarış kaybında tekrar deneyen LoanFacade üzerinden çağırır.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanService {

    /** Kopya tutan ödünçler: sınıra sayılır, kitabın silinmesini engeller. */
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
     * Bir kopya ödünç verir. Aynı kitabı iki üye alırsa Book.version ikinciyi düşürür. Bir üye iki
     * kitabı aynı anda alırsa üye satırı kilitlidir, ikinci istek bekler.
     *
     * <p>Token veritabanıyla karşılaştırılmadığı için rol burada güncel haliyle tekrar kontrol
     * edilir.
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
        // E-posta commit'i bekler; geri alınan ya da tekrarlanan ödünç bir şey duyurmaz.
        events.publishEvent(
                new LoanBorrowedEvent(
                        loan.getId(), member.getEmail(), book.getTitle(), loan.getDueDate()));
        return loanMapper.toResponse(loan);
    }

    /**
     * Ödüncü alan üye ya da admin. Kitap id'si istekte yok, cache kaydı sonuçtan (#result) silinir.
     */
    @CacheEvict(cacheNames = CacheNames.BOOKS, key = "#result.book().id()")
    @Transactional
    public LoanResponse returnBook(Long loanId, AuthenticatedUser caller) {
        Loan loan = getLoan(loanId);
        checkAccess(loan, caller);
        loan.markReturned(LocalDate.now(clock));
        loan.getBook().returnCopy();
        // Hemen yaz: cevapta yeni updatedAt olsun, kitaptaki çakışma bu metodun içinde düşsün.
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
     * Üye sadece kendi ödünçlerini görür ve iade eder, admin hepsini. Ödüncün sahibini veritabanı
     * bildiği için kural burada; id'ler gizli olmadığından 404 değil 403.
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
