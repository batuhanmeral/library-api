package com.batuhanmeral.library.loan;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Ödünç hep kitabı ve üyesiyle gelir (ikisi de to-one, sayfalamada güvenli). */
public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    @Override
    @EntityGraph(attributePaths = {"book", "member"})
    Optional<Loan> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findAll(Specification<Loan> specification, Pageable pageable);

    long countByMemberIdAndStatusIn(Long memberId, Collection<LoanStatus> statuses);

    boolean existsByMemberIdAndBookIdAndStatusIn(
            Long memberId, Long bookId, Collection<LoanStatus> statuses);

    /**
     * Üyenin süresi geçmiş ödüncü var mı: OVERDUE işaretlenmiş ya da hâlâ ACTIVE ama tarihi geçmiş.
     * Derived query "or"u ifade edemez, bu yüzden JPQL.
     */
    @Query(
            """
            select count(l) > 0 from Loan l
            where l.member.id = :memberId
              and (l.status = com.batuhanmeral.library.loan.LoanStatus.OVERDUE
                   or (l.status = com.batuhanmeral.library.loan.LoanStatus.ACTIVE
                       and l.dueDate < :today))
            """)
    boolean hasOverdueLoan(Long memberId, LocalDate today);

    /**
     * Süresi geçmiş aktif ödüncü olan ilk {@code limit} üye. Gece işi hep ilk sayfayı alır;
     * işaretlenen ödünçler sonraki sorgudan düşer, offset üye atlardı.
     */
    @Query(
            """
            select distinct l.member.id from Loan l
            where l.status = com.batuhanmeral.library.loan.LoanStatus.ACTIVE
              and l.dueDate < :today
            order by l.member.id
            """)
    List<Long> findMemberIdsWithLoansDueBefore(LocalDate today, Limit limit);

    /**
     * Okuduğu ödünçleri transaction bitene kadar kilitler; iş ve iade birbirinin üstüne yazamaz.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Loan> findLockedByMemberIdInAndStatusAndDueDateBefore(
            Collection<Long> memberIds, LoanStatus status, LocalDate today);

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<LoanStatus> statuses);

    boolean existsByBookId(Long bookId);
}
