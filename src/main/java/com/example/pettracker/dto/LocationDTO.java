package com.example.pettracker.dto;

public record LocationDTO(
        Long id,
        Long petId,
        double latitude,
        double longitude,
        Integer batteryPercent,
        Double batteryVoltage,
        String petName,
        String timestamp,
        String city,
        String address,
        String neighborhood,
        String comuna,
        Boolean neighborhoodResolved,
        String neighborhoodSource
) {}
