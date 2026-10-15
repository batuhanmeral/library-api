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

/** Gece işini elle başlatır; sadece admin (SecurityConfig: /admin/**). */
@Tag(name = "Admin jobs")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/jobs")
public class OverdueCheckController {

    private final OverdueLoanJob job;
    private final OverdueLoanJobRunner runner;

    /** 202: iş başladı, bitmedi. Hak burada alınır, ikinci istek hemen 409 alır. */
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
            // Executor işi reddetti (kuyruk dolu ya da kapanıyor): çalıştıran olmayacak.
            job.release();
            throw e;
        }
        return ResponseEntity.accepted().build();
    }
}
