package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.common.audit.AuditAction;
import com.batuhanmeral.library.common.audit.Auditable;
import com.batuhanmeral.library.common.web.PageResponse;
import com.batuhanmeral.library.common.web.SortableFields;
import com.batuhanmeral.library.loan.dto.LoanRequest;
import com.batuhanmeral.library.loan.dto.LoanResponse;
import com.batuhanmeral.library.loan.dto.LoanSearchCriteria;
import com.batuhanmeral.library.loan.dto.OwnLoanFilter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Uç nokta yetkileri SecurityConfig'te, ödüncün sahipliği LoanService'te kontrol edilir. */
@Tag(name = "Loans")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/loans")
public class LoanController {

    private static final SortableFields SORTABLE =
            SortableFields.of("id", "loanDate", "dueDate", "returnDate", "createdAt");

    private final LoanService loanService;
    private final LoanFacade loanFacade;

    /**
     * LoanFacade'de değil burada audit edilir: {@code @Retryable} proxy'si en dışta, orada her
     * deneme audit edilirdi.
     */
    @Auditable(action = AuditAction.LOAN_BORROW, target = "#request.bookId()")
    @PostMapping
    public ResponseEntity<LoanResponse> borrow(
            @Valid @RequestBody LoanRequest request,
            @AuthenticationPrincipal AuthenticatedUser member) {
        LoanResponse created = loanFacade.borrow(member.id(), request);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.id())
                        .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** İade bir eylemdir, istemcinin yazdığı alan değil; bu yüzden durum PATCH'i değil POST. */
    @Auditable(action = AuditAction.LOAN_RETURN, target = "#id")
    @PostMapping("/{id}/return")
    public LoanResponse returnBook(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return loanFacade.returnBook(id, caller);
    }

    /**
     * Çağıranın kendi ödünçleri, yeniden eskiye. Sabit yol parçası {id}'den önce gelir, "/me"
     * findById'ye gitmez.
     */
    @GetMapping("/me")
    public PageResponse<LoanResponse> ownLoans(
            @Valid @ParameterObject OwnLoanFilter filter,
            @AuthenticationPrincipal AuthenticatedUser member,
            @ParameterObject
                    @PageableDefault(
                            size = 20,
                            sort = {"loanDate", "id"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        LoanSearchCriteria criteria = new LoanSearchCriteria(filter.status(), member.id());
        return PageResponse.from(loanService.search(criteria, SORTABLE.check(pageable)));
    }

    @GetMapping("/{id}")
    public LoanResponse findById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return loanService.findById(id, caller);
    }

    /** Tüm üyelerin ödünçleri; sadece admin. */
    @GetMapping
    public PageResponse<LoanResponse> search(
            @Valid @ParameterObject LoanSearchCriteria criteria,
            @ParameterObject
                    @PageableDefault(
                            size = 20,
                            sort = {"loanDate", "id"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return PageResponse.from(loanService.search(criteria, SORTABLE.check(pageable)));
    }
}
