
package com.example.pettracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // hashed

    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Plan plan = Plan.BASIC;

    public enum Role { USER, WALKER, ADMIN }

    public enum Plan { BASIC, FAMILY, PREMIUM }
}
