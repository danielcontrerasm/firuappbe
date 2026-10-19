package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;

public record CachedPetAccessLookup(
        Long petId,
        Long ownerId,
        Pet.Status status,
        String terminalId
) {
    public static CachedPetAccessLookup from(Pet pet) {
        return new CachedPetAccessLookup(
                pet.getId(),
                pet.getOwner() == null ? null : pet.getOwner().getId(),
                pet.getStatus(),
                pet.getTerminalId()
        );
    }
}
