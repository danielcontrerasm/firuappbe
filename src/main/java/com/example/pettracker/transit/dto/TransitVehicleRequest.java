package com.example.pettracker.transit.dto;

public record TransitVehicleRequest(
        String label,
        String plateNumber,
        String routeCode,
        String operatorName,
        String type,
        String status,
        Long ownerId
) {
}
