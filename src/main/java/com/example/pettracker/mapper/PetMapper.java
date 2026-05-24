package com.example.pettracker.mapper;

import com.example.pettracker.dto.PetDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;

public class PetMapper {

    private PetMapper() {
    }

    public static PetDto toDto(Pet pet) {
        User owner = pet.getOwner();
        return new PetDto(
                pet.getId(),
                pet.getName(),
                pet.getType(),
                pet.getStatus() == null ? null : pet.getStatus().name(),
                pet.getImei(),
                pet.getCreatedAt(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getName(),
                owner == null ? null : owner.getEmail()
        );
    }
}
