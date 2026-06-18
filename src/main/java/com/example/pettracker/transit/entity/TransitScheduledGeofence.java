package com.example.pettracker.transit.entity;

import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "transit_scheduled_geofence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitScheduledGeofence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private TransitVehicle vehicle;

    private String name;

    @Enumerated(EnumType.STRING)
    private ZoneType zoneType;

    @Enumerated(EnumType.STRING)
    private ShapeType shapeType;

    private Double centerLat;

    private Double centerLng;

    private Double radiusMeters;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon polygon;

    private LocalTime activeFrom;

    private LocalTime activeTo;

    private String activeDays;

    @Builder.Default
    private Boolean enabled = true;

    public enum ZoneType {
        HOME,
        WORK
    }

    public enum ShapeType {
        CIRCLE,
        POLYGON
    }
}
