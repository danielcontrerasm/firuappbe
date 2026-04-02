package com.example.pettracker.dto;

public record GeofenceResponse(
        Long id,
        double centerLatitude,
        double centerLongitude,
        double radiusMeters
) {
}
