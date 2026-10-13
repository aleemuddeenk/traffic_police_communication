package com.clearpath.dto;

public record AlertAckResponseDto(
        Long alertId,
        String status,
        long acknowledgedCount,
        long notifiedCount,
        String message
) {}
