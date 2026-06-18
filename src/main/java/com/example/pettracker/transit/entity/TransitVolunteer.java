package com.example.pettracker.transit.entity;

import com.example.pettracker.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transit_volunteer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitVolunteer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_group_id")
    private TransitSearchGroup searchGroup;

    @Builder.Default
    private Instant joinedAt = Instant.now();

    @Builder.Default
    private Boolean active = true;
}
