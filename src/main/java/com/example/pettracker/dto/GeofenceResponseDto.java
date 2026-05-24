package com.example.pettracker.dto;

import java.util.List;

public record GeofenceResponseDto(
        Long id,
        Long petId,
        String petName,
        String type,
        Double centerLat,
        Double centerLng,
        Double radiusMeters,
        List<List<Double>> coordinates
) {
}
