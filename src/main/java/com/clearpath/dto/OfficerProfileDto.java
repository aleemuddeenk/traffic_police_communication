package com.clearpath.dto;

public record OfficerProfileDto(
        Long id,
        String officerId,
        String name,
        String rank,
        String badgeNumber,
        String station,
        String district,
        String phone,
        String photoUrl,
        boolean onDuty
) {}
