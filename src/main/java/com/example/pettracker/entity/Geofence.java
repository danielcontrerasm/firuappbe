package com.example.pettracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Polygon;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Geofence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Enumerated(EnumType.STRING)
    private Type type; // CIRCLE or POLYGON

    // circle fields
    private Double centerLat;
    private Double centerLng;
    private Double radiusMeters;

    // polygon
    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon polygon;

    public enum Type { CIRCLE, POLYGON }
}