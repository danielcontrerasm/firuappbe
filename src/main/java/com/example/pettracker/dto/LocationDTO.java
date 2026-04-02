package com.example.pettracker.dto;

public record LocationDTO(
        Long id,
        double latitude,
        double longitude,
        String petName,
        String timestamp
) {}
