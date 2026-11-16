package com.clearpath.controller;

import com.clearpath.dto.LocationPingDto;
import com.clearpath.model.Civilian;
import com.clearpath.model.CivilianLocationPing;
import com.clearpath.repository.CivilianLocationPingRepository;
import com.clearpath.repository.CivilianRepository;
import com.clearpath.security.CustomUserDetailsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/civilian/location")
public class CivilianLocationController {

    private final CivilianRepository civilianRepository;
    private final CivilianLocationPingRepository pingRepository;

    public CivilianLocationController(CivilianRepository civilianRepository,
                                      CivilianLocationPingRepository pingRepository) {
        this.civilianRepository = civilianRepository;
        this.pingRepository = pingRepository;
    }

    @PostMapping
    public ResponseEntity<?> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody LocationPingDto dto
    ) {
        Civilian civilian = resolveCivilian(userDetails);
        if (civilian == null) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN",
                    "message", "Only civilians can use this endpoint."));
        }

        Optional<CivilianLocationPing> existing = pingRepository.findByCivilian(civilian);
        CivilianLocationPing ping;
        if (existing.isPresent()) {
            ping = existing.get();
            ping.update(dto.latitude(), dto.longitude(), dto.heading(), dto.speed());
        } else {
            ping = new CivilianLocationPing(civilian, dto.latitude(), dto.longitude(), dto.heading(), dto.speed());
        }
        pingRepository.save(ping);

        // Update lastSeenAt on civilian
        civilian.setLastSeenAt(Instant.now());
        civilianRepository.save(civilian);

        return ResponseEntity.ok(Map.of(
                "status", "RECORDED",
                "latitude", dto.latitude(),
                "longitude", dto.longitude()
        ));
    }

    @DeleteMapping("/stop")
    public ResponseEntity<?> stopSharing(@AuthenticationPrincipal UserDetails userDetails) {
        Civilian civilian = resolveCivilian(userDetails);
        if (civilian == null) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        }

        pingRepository.findByCivilian(civilian).ifPresent(ping -> {
            ping.setActive(false);
            pingRepository.save(ping);
        });

        return ResponseEntity.ok(Map.of("status", "STOPPED"));
    }

    private Civilian resolveCivilian(UserDetails userDetails) {
        if (userDetails == null) return null;
        String username = userDetails.getUsername();
        if (!username.startsWith(CustomUserDetailsService.CIVILIAN_PREFIX)) return null;
        String phone = username.substring(CustomUserDetailsService.CIVILIAN_PREFIX.length());
        return civilianRepository.findByPhoneNumber(phone).orElse(null);
    }
}
