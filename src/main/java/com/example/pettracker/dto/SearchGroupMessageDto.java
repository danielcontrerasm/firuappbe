package com.example.pettracker.dto;

import java.time.Instant;

public record SearchGroupMessageDto(
        Long id,
        Long searchGroupId,
        Long senderId,
        String senderName,
        String body,
        Instant createdAt
) {
}
