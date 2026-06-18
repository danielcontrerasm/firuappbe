package com.example.pettracker.transit.dto;

import java.time.Instant;

public record TransitVehicleDto(
        Long id,
        String label,
        String plateNumber,
        String routeCode,
        String operatorName,
        String type,
        String status,
        Long ownerId,
        String ownerName,
        Instant createdAt
) {
}
