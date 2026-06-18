package com.example.pettracker.transit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transit_geofence_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitGeofenceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private TransitVehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_id")
    private TransitScheduledGeofence geofence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private TransitLocation location;

    @Enumerated(EnumType.STRING)
    private TransitScheduledGeofence.ZoneType zoneType;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(length = 1000)
    private String message;

    public enum EventType {
        OUTSIDE_ACTIVE_GEOFENCE
    }
}
