package com.example.pettracker.dto;

import jakarta.validation.constraints.NotNull;

public record GeofenceRequest(
        @NotNull Double centerLatitude,
        @NotNull Double centerLongitude,
        @NotNull Double radiusMeters
) {
}
