package com.example.pettracker.dto;

import java.util.Set;

public record AuthUserResponse(
        Long id,
        String fullName,
        String username,
        String email,
        Set<String> roles
) {
}
