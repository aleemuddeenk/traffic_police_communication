package com.clearpath.dto;

public record LoginResponse(
        String token,
        String tokenType,
        OfficerProfileDto profile
) {
    public LoginResponse(String token, OfficerProfileDto profile) {
        this(token, "Bearer", profile);
    }
}
