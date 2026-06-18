package com.example.pettracker.transit.dto;

import java.time.LocalDateTime;

public record TransitLocationDto(
        Long id,
        Long vehicleId,
        String vehicleLabel,
        double latitude,
        double longitude,
        LocalDateTime timestamp,
        String source
) {
}
