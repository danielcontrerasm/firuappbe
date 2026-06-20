package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "walk_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walker_profile_id", nullable = false)
    private DogWalkerProfile walkerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    private LocalDateTime requestedStart;

    private Integer durationMinutes;

    @Column(precision = 10, scale = 2)
    private BigDecimal ownerBudget;

    @Column(precision = 10, scale = 2)
    private BigDecimal walkerQuotedPrice;

    @Column(length = 1000)
    private String serviceAddress;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant createdAt;

    private Instant updatedAt;

    public enum Status {
        PENDING,
        NEGOTIATING,
        ACCEPTED,
        REJECTED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
}
