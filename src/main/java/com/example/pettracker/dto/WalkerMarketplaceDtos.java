package com.example.pettracker.dto;

import com.example.pettracker.entity.DogWalk;
import com.example.pettracker.entity.DogWalkerProfile;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.WalkRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;

public class WalkerMarketplaceDtos {

    @Data
    public static class WalkerApplicationRequest {
        private String name;
        private String email;
        private String password;
        private String phone;
        private String bio;
        private String neighborhood;
        private Integer experienceYears;
        private BigDecimal basePrice;
        private String priceNotes;
        private String services;
        private String availability;
    }

    @Data
    public static class AdminWalkerUpsertRequest {
        private String name;
        private String email;
        private String password;
        private String phone;
        private String bio;
        private String neighborhood;
        private Integer experienceYears;
        private BigDecimal basePrice;
        private String priceNotes;
        private String services;
        private String availability;
        private Boolean active;
        private DogWalkerProfile.ApprovalStatus approvalStatus;
    }

    @Data
    public static class WalkRequestCreateRequest {
        private Long walkerProfileId;
        private Long petId;
        private LocalDateTime requestedStart;
        private Integer durationMinutes;
        private BigDecimal ownerBudget;
        private String serviceAddress;
        private String notes;
    }

    @Data
    public static class WalkQuoteRequest {
        private BigDecimal walkerQuotedPrice;
        private WalkRequest.Status status;
    }

    @Data
    public static class WalkDecisionRequest {
        private WalkRequest.Status status;
    }

    @Data
    public static class WalkMessageRequest {
        private String body;
    }

    @Data
    public static class WalkPositionRequest {
        private double latitude;
        private double longitude;
    }

    public record CurrentUserResponse(
            Long id,
            String name,
            String email,
            String phone,
            User.Role role,
            Long walkerProfileId
    ) {}

    public record WalkerCardResponse(
            Long id,
            Long userId,
            String name,
            String email,
            String phone,
            String bio,
            String neighborhood,
            Integer experienceYears,
            BigDecimal basePrice,
            String priceNotes,
            String services,
            String availability,
            Boolean active,
            DogWalkerProfile.ApprovalStatus approvalStatus
    ) {}

    public record WalkRequestResponse(
            Long id,
            Long walkerProfileId,
            String walkerName,
            Long ownerId,
            String ownerName,
            Long petId,
            String petName,
            LocalDateTime requestedStart,
            Integer durationMinutes,
            BigDecimal ownerBudget,
            BigDecimal walkerQuotedPrice,
            String serviceAddress,
            String notes,
            WalkRequest.Status status,
            Instant createdAt,
            Instant updatedAt,
            Long activeDogWalkId
    ) {}

    public record WalkMessageResponse(
            Long id,
            Long walkRequestId,
            Long senderId,
            String senderName,
            String body,
            Instant createdAt
    ) {}

    public record DogWalkResponse(
            Long id,
            Long walkRequestId,
            Long walkerProfileId,
            String walkerName,
            Long ownerId,
            String ownerName,
            Long petId,
            String petName,
            BigDecimal agreedPrice,
            Instant startedAt,
            Instant completedAt,
            DogWalk.Status status,
            WalkPositionResponse lastPosition
    ) {}

    public record WalkPositionResponse(
            Long id,
            double latitude,
            double longitude,
            Instant recordedAt
    ) {}
}
