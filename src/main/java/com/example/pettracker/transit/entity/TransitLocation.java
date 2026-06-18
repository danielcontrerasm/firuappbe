package com.example.pettracker.transit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transit_location")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double latitude;

    private double longitude;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private TransitVehicle vehicle;
}
