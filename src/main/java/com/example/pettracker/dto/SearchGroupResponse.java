package com.example.pettracker.dto;

import java.time.Instant;
import java.util.List;

public record SearchGroupResponse(
        Long id,
        String title,
        String notes,
        boolean active,
        Instant createdAt,
        List<SearchVolunteerResponse> volunteers
) {
}
