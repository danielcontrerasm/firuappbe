package com.example.pettracker.dto;

public record VolunteerCandidateDto(
        Long userId,
        String name,
        String email
) {
}
