package com.example.pettracker.dto;

public record PetNeighborhoodDto(
        Long petId,
        double latitude,
        double longitude,
        String timestamp,
        String neighborhood,
        String district,
        String city,
        String displayName,
        String source,
        boolean resolved
) {
}
