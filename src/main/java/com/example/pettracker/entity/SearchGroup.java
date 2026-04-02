package com.example.pettracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id")
    private Pet pet;

    private String groupName;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(name = "searchgroup_volunteers",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> volunteers = new HashSet<>();
}