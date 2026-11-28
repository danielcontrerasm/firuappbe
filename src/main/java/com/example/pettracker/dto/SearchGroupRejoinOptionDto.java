package com.example.pettracker.dto;

public record SearchGroupRejoinOptionDto(
        Long groupId,
        String groupName,
        Long pendingRequestId
) {
}
