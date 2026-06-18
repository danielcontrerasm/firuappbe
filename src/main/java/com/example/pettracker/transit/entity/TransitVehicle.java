package com.example.pettracker.transit.entity;

import com.example.pettracker.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transit_vehicle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @Column(unique = true)
    private String plateNumber;

    private String routeCode;

    private String operatorName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VehicleType type = VehicleType.TAXI;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum VehicleType {
        TAXI,
        BUS
    }

    public enum VehicleStatus {
        ACTIVE,
        SEARCHING,
        OFFLINE
    }
}
