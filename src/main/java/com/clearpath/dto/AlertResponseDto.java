package com.clearpath.dto;

import java.time.Instant;
import java.util.List;

public record AlertResponseDto(
        Long alertId,
        SenderInfo sender,
        String roadName,
        Double latitude,
        Double longitude,
        Double headingDeg,
        String compassDirection,
        String ambulanceType,
        String ambulancePlate,
        String status,
        Double distanceKm,
        Double bearingDeg,
        List<String> connectingRoads,
        Instant createdAt,
        Instant expiresAt,
        long notifiedCount,
        long acknowledgedCount
) {
    public record SenderInfo(
            Long id,
            String officerId,
            String name,
            String rank,
            String badgeNumber,
            String station,
            String phone,
            String photoUrl
    ) {}
}
