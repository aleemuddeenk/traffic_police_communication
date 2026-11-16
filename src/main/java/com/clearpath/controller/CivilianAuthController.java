package com.clearpath.controller;

import com.clearpath.dto.CivilianProfileDto;
import com.clearpath.dto.SendOtpRequest;
import com.clearpath.dto.VerifyOtpRequest;
import com.clearpath.model.Civilian;
import com.clearpath.repository.CivilianRepository;
import com.clearpath.security.JwtTokenProvider;
import com.clearpath.service.OtpService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/civilian")
public class CivilianAuthController {

    private final OtpService otpService;
    private final CivilianRepository civilianRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public CivilianAuthController(OtpService otpService,
                                  CivilianRepository civilianRepository,
                                  JwtTokenProvider jwtTokenProvider) {
        this.otpService = otpService;
        this.civilianRepository = civilianRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * POST /api/auth/civilian/send-otp
     * Sends (or mocks sending) an OTP to the given phone number.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody SendOtpRequest request) {
        if (request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_PHONE",
                    "message", "Phone number is required."
            ));
        }

        String demoOtp = otpService.sendOtp(request.phoneNumber());

        // In production, never return the OTP in the response — send via SMS only.
        // For demo purposes, we return it so the UI can show it.
        return ResponseEntity.ok(Map.of(
                "message", "OTP sent to " + request.phoneNumber(),
                "demoOtp", demoOtp   // REMOVE IN PRODUCTION
        ));
    }

    /**
     * POST /api/auth/civilian/verify-otp
     * Verifies the OTP and issues a JWT with ROLE_CIVILIAN.
     * Auto-registers the phone number on first login.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        if (request.phoneNumber() == null || request.otp() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_FIELDS",
                    "message", "Phone number and OTP are required."
            ));
        }

        boolean valid = otpService.verifyOtp(request.phoneNumber(), request.otp());
        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "INVALID_OTP",
                    "message", "OTP is invalid or has expired. Please request a new one."
            ));
        }

        // Auto-register civilian on first login
        Civilian civilian = civilianRepository.findByPhoneNumber(normalise(request.phoneNumber()))
                .orElseGet(() -> {
                    Civilian newCivilian = new Civilian(normalise(request.phoneNumber()), "Civilian");
                    return civilianRepository.save(newCivilian);
                });

        // JWT subject = phone number, role = ROLE_CIVILIAN
        String token = jwtTokenProvider.generateToken(civilian.getPhoneNumber(), "ROLE_CIVILIAN");

        CivilianProfileDto profile = new CivilianProfileDto(
                civilian.getId(),
                civilian.getPhoneNumber(),
                civilian.getDisplayName(),
                civilian.getRole()
        );

        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", "CIVILIAN",
                "profile", profile
        ));
    }

    private String normalise(String phone) {
        return phone == null ? "" : phone.trim().replaceAll("[^+\\d]", "");
    }
}
