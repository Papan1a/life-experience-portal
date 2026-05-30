package com.lep.portal.admin;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lep.portal.user.PortalUserDetails;

@RestController
public class ReportController {

    private static final Set<String> VALID_REASONS = Set.of(
            "duplicate", "unsafe", "spam", "wrong_category", "bad_description", "other");

    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @PostMapping("/reports")
    public ResponseEntity<String> report(@AuthenticationPrincipal PortalUserDetails principal,
                                         @RequestParam("target_type") String targetType,
                                         @RequestParam("target_id") UUID targetId,
                                         @RequestParam("reason") String reason,
                                         @RequestParam(value = "comment", required = false) String comment) {

        if (!VALID_REASONS.contains(reason)) {
            return ResponseEntity.badRequest().body("Недопустимая причина жалобы");
        }

        Report report = new Report();
        report.setReporterUserId(principal.getUserId());
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        if (comment != null) {
            String trimmed = comment.length() > 200 ? comment.substring(0, 200) : comment;
            report.setComment(trimmed);
        }
        report.setStatus("NEW");

        reportRepository.save(report);

        return ResponseEntity.ok("Спасибо, ваша жалоба принята. Мы рассмотрим её.");
    }
}
