package com.clearpath.controller;

import com.clearpath.dto.AlertAckResponseDto;
import com.clearpath.dto.AlertResponseDto;
import com.clearpath.dto.CreateAlertRequest;
import com.clearpath.model.Officer;
import com.clearpath.repository.OfficerRepository;
import com.clearpath.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final OfficerRepository officerRepository;

    public AlertController(AlertService alertService, OfficerRepository officerRepository) {
        this.alertService = alertService;
        this.officerRepository = officerRepository;
    }

    @PostMapping
    public ResponseEntity<?> createAlert(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAlertRequest request
    ) {
        Officer sender = getOfficer(userDetails);
        try {
            AlertResponseDto alert = alertService.triggerAlert(sender, request);
            return ResponseEntity.ok(alert);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ALERT_TRIGGER_REJECTED",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<?> acknowledgeAlert(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long alertId
    ) {
        Officer officer = getOfficer(userDetails);
        try {
            AlertAckResponseDto response = alertService.acknowledgeAlert(officer, alertId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<?> dismissAlert(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long alertId
    ) {
        Officer officer = getOfficer(userDetails);
        try {
            AlertAckResponseDto response = alertService.dismissAlert(officer, alertId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeAlert(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long alertId
    ) {
        Officer officer = getOfficer(userDetails);
        try {
            AlertAckResponseDto response = alertService.markAmbulancePassed(officer, alertId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<AlertResponseDto>> getActiveAlerts(@AuthenticationPrincipal UserDetails userDetails) {
        Officer officer = getOfficer(userDetails);
        return ResponseEntity.ok(alertService.getActiveAlertsForOfficer(officer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAlertById(@PathVariable("id") Long alertId) {
        return alertService.getAlertStatus(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<AlertResponseDto>> getAlertHistory(@AuthenticationPrincipal UserDetails userDetails) {
        Officer officer = getOfficer(userDetails);
        return ResponseEntity.ok(alertService.getOfficerAlertHistory(officer));
    }

    private Officer getOfficer(UserDetails userDetails) {
        return officerRepository.findByOfficerId(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Officer not found: " + userDetails.getUsername()));
    }
}
