package com.example.pettracker.dto;

import java.time.Instant;

public class PetShareDtos {
    private PetShareDtos() {
    }

    public record ShareRequest(String email) {
    }

    public record SharedPerson(Long userId, String name, String email, Instant sharedAt) {
    }
}
