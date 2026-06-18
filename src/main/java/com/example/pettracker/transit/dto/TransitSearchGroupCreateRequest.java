package com.example.pettracker.transit.dto;

import java.util.List;

public record TransitSearchGroupCreateRequest(
        Long vehicleId,
        String groupName,
        String description,
        String status,
        String area,
        String city,
        Double coverageRadiusKm,
        String leaderName,
        String leaderPhone,
        String incidentType,
        List<Long> volunteerUserIds
) {
}
