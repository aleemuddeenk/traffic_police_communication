package com.clearpath.controller;

import com.clearpath.model.DeviceToken;
import com.clearpath.model.Officer;
import com.clearpath.repository.DeviceTokenRepository;
import com.clearpath.repository.OfficerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/device")
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;
    private final OfficerRepository officerRepository;

    public DeviceTokenController(DeviceTokenRepository deviceTokenRepository, OfficerRepository officerRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.officerRepository = officerRepository;
    }

    @PostMapping("/token")
    public ResponseEntity<?> registerToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body
    ) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "FCM token is required"));
        }

        Officer officer = officerRepository.findByOfficerId(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Officer not found"));

        Optional<DeviceToken> existing = deviceTokenRepository.findByOfficerAndFcmToken(officer, token);
        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            dt.setUpdatedAt(Instant.now());
            deviceTokenRepository.save(dt);
        } else {
            deviceTokenRepository.save(new DeviceToken(officer, token));
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "FCM token registered"));
    }
}
