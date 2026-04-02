package com.example.pettracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    @Column(columnDefinition = "text")
    private String metadata; // JSON or plain text

    private Instant createdAt = Instant.now();

    public enum AlertType { BOUNDARY, LOST, MANUAL }
    public enum Status { OPEN, CLOSED }
}