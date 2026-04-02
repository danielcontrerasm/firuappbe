package com.example.pettracker.dto;

import java.time.Instant;

public record PetSummaryResponse(
        Long id,
        String name,
        String species,
        String breed,
        String description,
        boolean lost,
        Instant lostSince,
        Long ownerId,
        String ownerName,
        LocationResponse latestLocation,
        GeofenceResponse geofence
) {
}
