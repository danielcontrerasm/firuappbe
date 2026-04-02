package com.example.pettracker.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchGroupCreateRequest(
        @NotBlank String title,
        String notes
) {
}
