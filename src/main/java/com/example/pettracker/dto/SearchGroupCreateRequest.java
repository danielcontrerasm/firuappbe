package com.example.pettracker.dto;

import java.util.List;

public record SearchGroupCreateRequest(
        Long petId,
        String groupName,
        String description,
        String status,
        String area,
        String city,
        Double coverageRadiusKm,
        String petName,
        String petStatus,
        String leaderName,
        String leaderPhone,
        Long createdById,
        String createdByName,
        String createdByEmail,
        List<Long> volunteerUserIds
) {
}
