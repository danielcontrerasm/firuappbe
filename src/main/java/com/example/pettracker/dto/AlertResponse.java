package com.example.pettracker.dto;

import java.time.Instant;

public record AlertResponse(
        Long id,
        String type,
        String message,
        Instant createdAt
) {
}
