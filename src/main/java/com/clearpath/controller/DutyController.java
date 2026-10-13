package com.clearpath.controller;

import com.clearpath.model.DutySession;
import com.clearpath.model.Officer;
import com.clearpath.repository.OfficerRepository;
import com.clearpath.service.DutyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/duty")
public class DutyController {

    private final DutyService dutyService;
    private final OfficerRepository officerRepository;

    public DutyController(DutyService dutyService, OfficerRepository officerRepository) {
        this.dutyService = dutyService;
        this.officerRepository = officerRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startDuty(@AuthenticationPrincipal UserDetails userDetails) {
        Officer officer = getOfficer(userDetails);
        DutySession session = dutyService.startDuty(officer);

        return ResponseEntity.ok(Map.of(
                "status", "ON_DUTY",
                "sessionId", session.getId(),
                "startedAt", session.getStartedAt(),
                "message", "Officer is now ON DUTY. Live GPS tracking enabled."
        ));
    }

    @PostMapping("/end")
    public ResponseEntity<?> endDuty(@AuthenticationPrincipal UserDetails userDetails) {
        Officer officer = getOfficer(userDetails);
        dutyService.endDuty(officer);

        return ResponseEntity.ok(Map.of(
                "status", "OFF_DUTY",
                "message", "Officer is now OFF DUTY. Live tracking terminated."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        Officer officer = getOfficer(userDetails);
        boolean onDuty = dutyService.isOnDuty(officer);

        return ResponseEntity.ok(Map.of(
                "onDuty", onDuty,
                "status", onDuty ? "ON_DUTY" : "OFF_DUTY"
        ));
    }

    private Officer getOfficer(UserDetails userDetails) {
        return officerRepository.findByOfficerId(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Officer record not found"));
    }
}
