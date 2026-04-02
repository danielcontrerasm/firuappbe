package com.example.pettracker.dto;

import java.time.Instant;

public record LocationResponse(
        Long id,
        double latitude,
        double longitude,
        Double speed,
        Double accuracyMeters,
        Instant recordedAt
) {
}
