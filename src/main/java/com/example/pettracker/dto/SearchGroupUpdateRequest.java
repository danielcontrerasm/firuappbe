package com.example.pettracker.dto;

public record SearchGroupUpdateRequest(
        String groupName,
        String description,
        String status,
        String area,
        String city,
        String leaderName,
        String leaderPhone,
        Double coverageRadiusKm
) {
}
