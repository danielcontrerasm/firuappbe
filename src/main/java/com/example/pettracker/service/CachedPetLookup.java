package com.example.pettracker.service;

import java.time.Duration;

public record CachedPetLookup(Long petId, String petName, Duration ttl) {
    public boolean found() {
        return petId != null;
    }
}
