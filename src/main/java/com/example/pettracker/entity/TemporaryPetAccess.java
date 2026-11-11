package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import lombok.*;

@Entity
@Table(name = "temporary_pet_access")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemporaryPetAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private Boolean recurring = false;

    /** Comma-separated java.time.DayOfWeek names, e.g. MONDAY,TUESDAY. */
    private String recurringDays;

    private LocalTime recurringStartTime;
    private LocalTime recurringEndTime;

    @Builder.Default
    private Boolean canViewLocation = true;

    @Builder.Default
    private Boolean canViewAlerts = true;

    @Builder.Default
    private Boolean canViewHistory = true;

    @Builder.Default
    private Boolean active = true;

    private String invitationTokenHash;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant acceptedAt;
    private Instant revokedAt;
}
