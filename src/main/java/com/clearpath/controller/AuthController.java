package com.clearpath.controller;

import com.clearpath.dto.LoginRequest;
import com.clearpath.dto.LoginResponse;
import com.clearpath.dto.OfficerProfileDto;
import com.clearpath.model.Officer;
import com.clearpath.repository.OfficerRepository;
import com.clearpath.security.JwtTokenProvider;
import com.clearpath.security.PoliceVerificationService;
import com.clearpath.service.DutyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final PoliceVerificationService policeVerificationService;
    private final OfficerRepository officerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final DutyService dutyService;

    public AuthController(
            PoliceVerificationService policeVerificationService,
            OfficerRepository officerRepository,
            JwtTokenProvider jwtTokenProvider,
            DutyService dutyService
    ) {
        this.policeVerificationService = policeVerificationService;
        this.officerRepository = officerRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.dutyService = dutyService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<PoliceVerificationService.VerifiedProfile> verifiedOpt =
                policeVerificationService.verifyAndFetchRecord(request.officerId(), request.password());

        if (verifiedOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Authentication Failed",
                            "message", "Invalid Officer ID or Password. Only verified police officers are authorized."
                    ));
        }

        PoliceVerificationService.VerifiedProfile profile = verifiedOpt.get();
        Officer officer = officerRepository.findByOfficerId(profile.officerId())
                .orElseThrow(() -> new IllegalStateException("Officer record synchronization error"));

        String token = jwtTokenProvider.generateToken(officer.getOfficerId());
        boolean onDuty = dutyService.isOnDuty(officer);

        OfficerProfileDto profileDto = new OfficerProfileDto(
                officer.getId(),
                officer.getOfficerId(),
                officer.getName(),
                officer.getRank(),
                officer.getBadgeNumber(),
                officer.getStation(),
                officer.getDistrict(),
                officer.getPhone(),
                officer.getPhotoUrl(),
                onDuty
        );

        return ResponseEntity.ok(new LoginResponse(token, profileDto));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Officer officer = officerRepository.findByOfficerId(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Officer not found: " + userDetails.getUsername()));

        boolean onDuty = dutyService.isOnDuty(officer);

        OfficerProfileDto profileDto = new OfficerProfileDto(
                officer.getId(),
                officer.getOfficerId(),
                officer.getName(),
                officer.getRank(),
                officer.getBadgeNumber(),
                officer.getStation(),
                officer.getDistrict(),
                officer.getPhone(),
                officer.getPhotoUrl(),
                onDuty
        );

        return ResponseEntity.ok(profileDto);
    }
}
