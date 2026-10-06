package com.example.pettracker.dto;

import java.time.LocalDate;

public record PetWalkDistanceDayDto(
        Long petId,
        LocalDate date,
        String dayOfWeek,
        double distanceKm
) {
}
