package com.example.pettracker.transit.dto;

import java.time.LocalDateTime;

public record TransitLocationRequest(
        double latitude,
        double longitude,
        LocalDateTime timestamp,
        String source
) {
}
