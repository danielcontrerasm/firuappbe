package com.example.pettracker.dto;

public record LocationDTO(
        Long id,
        Long petId,
        double latitude,
        double longitude,
        String petName,
        String timestamp
) {}
