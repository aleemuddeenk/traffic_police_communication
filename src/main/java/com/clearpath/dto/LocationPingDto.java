package com.clearpath.dto;

import jakarta.validation.constraints.NotNull;

public record LocationPingDto(
        @NotNull(message = "Latitude is required") Double latitude,
        @NotNull(message = "Longitude is required") Double longitude,
        Double heading,
        Double speed
) {}
