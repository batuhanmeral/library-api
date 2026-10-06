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

/** A loan is always returned with its book and member (both to-one, safe to fetch when paging). */
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
     * Whether the member has a loan past its due date: one the nightly job already marked OVERDUE
     * (step 7), or one still ACTIVE whose due date has passed since the job last ran. A derived
     * query cannot express the "or", hence JPQL; enum constants are written with their class name.
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
     * The first {@code limit} members, by id, with an active loan past its due date. The nightly
     * job takes this first page again and again: each batch marks its loans overdue, so they drop
     * out of the next query. Paging with an offset would skip members instead.
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
     * Locks the loans it reads until the transaction ends. A return that reads one of them in the
     * meantime waits before writing, so the job and the return cannot overwrite each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Loan> findLockedByMemberIdInAndStatusAndDueDateBefore(
            Collection<Long> memberIds, LoanStatus status, LocalDate today);

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<LoanStatus> statuses);

    boolean existsByBookId(Long bookId);
}
