package com.example.pettracker.dto;

import java.time.Instant;

public record VolunteerDto(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String userPhone,
        Long searchGroupId,
        String searchGroupName,
        Long petId,
        String petName,
        Instant joinedAt,
        Boolean active
) {
}
