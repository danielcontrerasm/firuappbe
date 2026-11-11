package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** A person explicitly authorized to view and follow one pet. */
@Entity
@Table(name = "pet_authorized_users", uniqueConstraints = @UniqueConstraint(
        name = "ux_pet_authorized_user", columnNames = {"pet_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
