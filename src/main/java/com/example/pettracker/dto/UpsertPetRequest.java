package com.example.pettracker.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertPetRequest(
        @NotBlank String name,
        String species,
        String breed,
        String description,
        Long ownerId
) {
}
