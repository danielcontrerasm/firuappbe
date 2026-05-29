package com.example.pettracker.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
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
}
