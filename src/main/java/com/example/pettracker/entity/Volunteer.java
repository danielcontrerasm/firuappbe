package com.example.pettracker.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_volunteer_user_search_group",
        columnNames = {"user_id", "search_group_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Volunteer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private SearchGroup searchGroup;

    @Builder.Default
    private Instant joinedAt = Instant.now();

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean revoked = false;
}
