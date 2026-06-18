package com.example.pettracker.transit.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record TransitReportDto(
        Long vehicleId,
        String vehicleLabel,
        String window,
        LocalDateTime from,
        LocalDateTime to,
        Instant generatedAt,
        long locationCount,
        double totalDistanceKm,
        long outsideHomeGeofenceEvents,
        long outsideWorkGeofenceEvents,
        int activeSearchGroups,
        LocalDateTime latestLocationAt
) {
}
