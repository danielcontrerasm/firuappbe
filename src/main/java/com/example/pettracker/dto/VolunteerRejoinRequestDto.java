package com.example.pettracker.dto;

import java.time.Instant;

public record VolunteerRejoinRequestDto(
        Long id,
        Long searchGroupId,
        Long volunteerUserId,
        String volunteerName,
        String status,
        Instant createdAt,
        Instant resolvedAt) {
}
