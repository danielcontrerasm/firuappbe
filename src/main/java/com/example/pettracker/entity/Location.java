package com.example.pettracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "location", indexes = {
        @Index(name = "idx_location_pet_timestamp", columnList = "pet_id,timestamp"),
        @Index(name = "idx_location_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double latitude;
    private double longitude;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private Integer batteryPercent;

    private Double batteryVoltage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;
}
