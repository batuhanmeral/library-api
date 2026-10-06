package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.loan.dto.LoanSearchCriteria;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;

/** WHERE clause of GET /loans from the filters that were sent; see BookSpecifications. */
final class LoanSpecifications {

    private LoanSpecifications() {}

    static Specification<Loan> matching(LoanSearchCriteria criteria) {
        List<PredicateSpecification<Loan>> filters = new ArrayList<>();
        if (criteria.status() != null) {
            filters.add((loan, cb) -> cb.equal(loan.get("status"), criteria.status()));
        }
        if (criteria.memberId() != null) {
            filters.add((loan, cb) -> cb.equal(loan.get("member").get("id"), criteria.memberId()));
        }
        return Specification.where(PredicateSpecification.allOf(filters));
    }
}
