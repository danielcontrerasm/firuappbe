package com.example.pettracker.dto;

import java.time.Instant;

public record PetDto(
        Long id,
        String name,
        String type,
        String status,
        String imei,
        Instant createdAt,
        Long ownerId,
        String ownerName,
        String ownerEmail
) {
}
