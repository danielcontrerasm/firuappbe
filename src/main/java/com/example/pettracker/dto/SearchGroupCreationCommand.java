package com.example.pettracker.dto;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Internal command containing the fields needed to create a search group. */
public record SearchGroupCreationCommand(
        String groupName,
        String visibility,
        String description,
        String status,
        String area,
        String city,
        String leaderName,
        String leaderPhone,
        Double coverageRadiusKm,
        List<Long> volunteerUserIds,
        String whatsappGroupLink,
        MultipartFile image
) {
}
