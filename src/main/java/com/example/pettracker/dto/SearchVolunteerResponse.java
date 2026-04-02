package com.example.pettracker.dto;

import java.time.Instant;

public record SearchVolunteerResponse(
        Long id,
        Long userId,
        String userName,
        String username,
        Instant joinedAt
) {
}
