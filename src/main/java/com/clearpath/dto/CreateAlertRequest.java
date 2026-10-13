package com.clearpath.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAlertRequest(
        @NotNull(message = "Latitude is required") Double latitude,
        @NotNull(message = "Longitude is required") Double longitude,
        Double headingDeg,
        String compassDirection,
        String ambulanceType,
        String ambulancePlate
) {}
