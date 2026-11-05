package com.example.pettracker.dto;

import java.time.Instant;

public record AlertDto(
        Long id,
        Long petId,
        String petName,
        String alertType,
        String status,
        String metadata,
        Instant createdAt
) {
}
