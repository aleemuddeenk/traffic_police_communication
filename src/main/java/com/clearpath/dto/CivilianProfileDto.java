package com.clearpath.dto;

public record CivilianProfileDto(
        Long id,
        String phoneNumber,
        String displayName,
        String role
) {}
