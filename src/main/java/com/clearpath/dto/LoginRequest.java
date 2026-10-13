package com.clearpath.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Officer ID is required") String officerId,
        @NotBlank(message = "Password is required") String password
) {}
