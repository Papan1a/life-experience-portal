package com.lep.portal.admin;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lep.portal.user.PortalUserDetails;

@RestController
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private static final Set<String> VALID_REASONS = Set.of(
            "duplicate", "unsafe", "spam", "wrong_category", "bad_description", "other");

    @PostMapping("/reports")
    public ResponseEntity<String> report(@AuthenticationPrincipal PortalUserDetails principal,
                                         @RequestParam("target_type") String targetType,
                                         @RequestParam("target_id") UUID targetId,
                                         @RequestParam("reason") String reason,
                                         @RequestParam(value = "comment", required = false) String comment) {

        if (!VALID_REASONS.contains(reason)) {
            return ResponseEntity.badRequest().body("Недопустимая причина жалобы");
        }

        log.warn("REPORT: user={} target_type={} target_id={} reason={} comment={}",
                principal.getUserId(), targetType, targetId, reason,
                comment != null ? comment : "<нет>");

        return ResponseEntity.ok("Спасибо, ваша жалоба принята. Мы рассмотрим её.");
    }
}
