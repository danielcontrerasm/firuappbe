package com.example.pettracker.dto;

import java.time.Instant;

public record PetDto(
        Long id,
        String name,
        String type,
        String race,
        Integer age,
        Double weight,
        String status,
        String statusLabel,
        String imei,
        Instant createdAt,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        String imageUrl,
        String city,
        String comuna,
        String neighborhood
) {
}
