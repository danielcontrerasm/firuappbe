package com.example.pettracker.dto;

public record SearchGroupUpdateRequest(
        String groupName,
        String visibility,
        String description,
        String status,
        String area,
        String city,
        String leaderName,
        String leaderPhone,
        Double coverageRadiusKm,
        String whatsappGroupLink
) {
    public SearchGroupUpdateRequest(
            String groupName,
            String description,
            String status,
            String area,
            String city,
            String leaderName,
            String leaderPhone,
            Double coverageRadiusKm,
            String whatsappGroupLink) {
        this(groupName, null, description, status, area, city, leaderName, leaderPhone,
                coverageRadiusKm, whatsappGroupLink);
    }
}
