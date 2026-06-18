package com.example.pettracker.transit.dto;

import java.time.Instant;
import java.util.List;

public record TransitSearchGroupDto(
        Long id,
        Long vehicleId,
        String vehicleLabel,
        String groupName,
        String description,
        String status,
        String area,
        String city,
        Double coverageRadiusKm,
        String leaderName,
        String leaderPhone,
        String incidentType,
        Instant createdAt,
        Long createdById,
        String createdByName,
        int membersCount,
        List<Long> volunteerUserIds
) {
}
