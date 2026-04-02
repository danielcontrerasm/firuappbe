package com.example.pettracker.dto;

import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
public class LocationRequest {
    private double latitude;
    private double longitude;
    private LocalDateTime timestamp; // optional
}
