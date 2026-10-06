package com.batuhanmeral.library.loan;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<LoanStatus> statuses);

    boolean existsByBookId(Long bookId);
}
