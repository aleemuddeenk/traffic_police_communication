package com.clearpath.controller;

import com.clearpath.dto.LocationPingDto;
import com.clearpath.model.LocationPing;
import com.clearpath.model.Officer;
import com.clearpath.repository.OfficerRepository;
import com.clearpath.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locationService;
    private final OfficerRepository officerRepository;

    public LocationController(LocationService locationService, OfficerRepository officerRepository) {
        this.locationService = locationService;
        this.officerRepository = officerRepository;
    }

    @PostMapping
    public ResponseEntity<?> pingLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LocationPingDto dto
    ) {
        Officer officer = officerRepository.findByOfficerId(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Officer not found"));

        try {
            LocationPing ping = locationService.recordLocation(officer, dto);
            return ResponseEntity.ok(Map.of(
                    "status", "RECORDED",
                    "latitude", ping.getLatitude(),
                    "longitude", ping.getLongitude(),
                    "updatedAt", ping.getUpdatedAt()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "OFF_DUTY",
                    "message", e.getMessage()
            ));
        }
    }
}
