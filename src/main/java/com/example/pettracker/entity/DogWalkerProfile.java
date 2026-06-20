package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dog_walker_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DogWalkerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 1500)
    private String bio;

    private String neighborhood;

    private Integer experienceYears;

    @Column(precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(length = 1000)
    private String priceNotes;

    @Column(length = 1000)
    private String services;

    @Column(length = 1000)
    private String availability;

    private Boolean active;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;

    private Instant createdAt;

    private Instant updatedAt;

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
