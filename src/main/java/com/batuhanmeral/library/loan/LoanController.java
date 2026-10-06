package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.common.web.PageResponse;
import com.batuhanmeral.library.common.web.SortableFields;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.loan.dto.LoanResponse;
import com.batuhanmeral.library.loan.dto.LoanSearchCriteria;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/loans")
public class LoanController {

    private static final SortableFields SORTABLE =
            SortableFields.of("id", "loanDate", "dueDate", "returnDate", "createdAt");

    private final LoanService loanService;

    @PostMapping
    public ResponseEntity<LoanResponse> borrow(@Valid @RequestBody LoanRequest request) {
        LoanResponse created = loanService.borrow(request);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.id())
                        .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Returning is an action on a loan, not a field a client sets, so it is a POST to a
     * sub-resource rather than a PATCH of the status.
     */
    @PostMapping("/{id}/return")
    public LoanResponse returnBook(@PathVariable Long id) {
        return loanService.returnBook(id);
    }

    @GetMapping("/{id}")
    public LoanResponse findById(@PathVariable Long id) {
        return loanService.findById(id);
    }

    @GetMapping
    public PageResponse<LoanResponse> search(
            @Valid LoanSearchCriteria criteria,
            @PageableDefault(
                            size = 20,
                            sort = {"loanDate", "id"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return PageResponse.from(loanService.search(criteria, SORTABLE.check(pageable)));
    }
}
