package com.example.pettracker.transit.dto;

import java.util.List;

public record TransitScheduledGeofenceDto(
        Long id,
        Long vehicleId,
        String name,
        String zoneType,
        String shapeType,
        Double centerLat,
        Double centerLng,
        Double radiusMeters,
        List<List<Double>> coordinates,
        String activeFrom,
        String activeTo,
        String activeDays,
        Boolean enabled
) {
}
