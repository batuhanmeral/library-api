package com.batuhanmeral.library.loan;

import com.batuhanmeral.library.common.audit.AuditAction;
import com.batuhanmeral.library.common.audit.Auditable;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts the nightly overdue check by hand. Administrators only (SecurityConfig: /admin/**). */
@Tag(name = "Admin jobs")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/jobs")
public class OverdueCheckController {

    private final OverdueLoanJob job;
    private final OverdueLoanJobRunner runner;

    /**
     * 202 Accepted: the job has started, not finished. The claim is taken here, in the request's
     * thread, so a second request gets its 409 at once instead of a 202 for a run that never
     * happens.
     */
    @Auditable(action = AuditAction.OVERDUE_CHECK_START)
    @PostMapping("/overdue-check")
    public ResponseEntity<Void> startOverdueCheck() {
        if (!job.tryClaim()) {
            throw new BusinessRuleException(
                    ErrorCode.JOB_ALREADY_RUNNING, "The overdue check is already running.");
        }
        try {
            runner.runClaimed();
        } catch (RuntimeException e) {
            // The executor refused the task (queue full, shutting down): nobody will run it.
            job.release();
            throw e;
        }
        return ResponseEntity.accepted().build();
    }
}
