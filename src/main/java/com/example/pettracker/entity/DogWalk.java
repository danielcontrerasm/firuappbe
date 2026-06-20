package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dog_walks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DogWalk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walk_request_id", nullable = false, unique = true)
    private WalkRequest walkRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walker_profile_id", nullable = false)
    private DogWalkerProfile walkerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Column(precision = 10, scale = 2)
    private BigDecimal agreedPrice;

    private Instant startedAt;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
}
