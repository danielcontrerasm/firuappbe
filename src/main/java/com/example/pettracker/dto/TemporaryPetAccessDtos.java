package com.example.pettracker.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

public class TemporaryPetAccessDtos {
    private TemporaryPetAccessDtos() {
    }

    public record CreateRequest(
            Long userId,
            String email,
            Instant startsAt,
            Instant expiresAt,
            Boolean recurring,
            Set<String> recurringDays,
            LocalTime recurringStartTime,
            LocalTime recurringEndTime,
            Boolean canViewLocation,
            Boolean canViewAlerts,
            Boolean canViewHistory
    ) {
    }

    public record Response(
            Long id,
            Long petId,
            Long userId,
            String userName,
            String userEmail,
            Instant startsAt,
            Instant expiresAt,
            Boolean recurring,
            Set<String> recurringDays,
            LocalTime recurringStartTime,
            LocalTime recurringEndTime,
            Boolean canViewLocation,
            Boolean canViewAlerts,
            Boolean canViewHistory,
            Boolean active,
            Boolean accepted,
            Boolean currentlyActive
    ) {
    }

    public record AcceptRequest(String token) {
    }

    public record AcceptResponse(String message, Long accessId, String email) {
    }
}
