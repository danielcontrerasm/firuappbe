package com.example.pettracker.dto;

import lombok.*;

@Data
public class LocationRequest {
    private double latitude;
    private double longitude;
    private String timestamp; // optional ISO-8601 timestamp; UTC/offset values are converted to the app timezone
    private Integer batteryPercent;
    private Double batteryVoltage;
}
