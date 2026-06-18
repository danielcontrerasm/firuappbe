package com.example.pettracker.transit.entity;

import com.example.pettracker.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transit_search_group")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitSearchGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private TransitVehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private String groupName;

    @Column(length = 1000)
    private String description;

    @Builder.Default
    private String status = "active";

    private String area;

    private String city;

    private String leaderName;

    private String leaderPhone;

    private Double coverageRadiusKm;

    private String incidentType;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(
            name = "transit_searchgroup_volunteers",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> volunteers = new HashSet<>();
}
