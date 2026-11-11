package com.example.pettracker.dto;

import java.time.Instant;
import java.util.List;

public record SearchGroupDto(
        Long id,
        String groupName,
        String visibility,
        String description,
        String status,
        String area,
        String city,
        int membersCount,
        String leaderName,
        String leaderPhone,
        Double coverageRadiusKm,
        Instant createdAt,
        Long petId,
        String petName,
        String petStatus,
        String imageUrl,
        String whatsappGroupLink,
        Long createdById,
        String createdByName,
        String createdByEmail,
        List<VolunteerMemberDto> volunteers
) {
}
