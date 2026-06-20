package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dog_walk_positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DogWalkPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dog_walk_id", nullable = false)
    private DogWalk dogWalk;

    private double latitude;

    private double longitude;

    private Instant recordedAt;
}
