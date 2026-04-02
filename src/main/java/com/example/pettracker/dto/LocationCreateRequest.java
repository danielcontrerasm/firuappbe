package com.example.pettracker.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record LocationCreateRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        Double speed,
        Double accuracyMeters,
        Instant recordedAt
) {
}
