package com.example.pettracker.dto;

public record VolunteerMemberDto(
        Long userId,
        String name,
        String email,
        String phone
) {
}
