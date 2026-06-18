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
                pet.getRace(),
                pet.getAge(),
                pet.getWeight(),
                pet.getStatus() == null ? null : pet.getStatus().name(),
                statusLabel(pet),
                pet.getImei(),
                pet.getCreatedAt(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getName(),
                owner == null ? null : owner.getEmail(),
                pet.getImageContentType() == null ? null : "/api/pets/" + pet.getId() + "/image",
                null,
                null,
                null
        );
    }

    private static String statusLabel(Pet pet) {
        if (pet.getStatus() == null) {
            return null;
        }
        return pet.getStatus() == Pet.Status.LOST ? "Lost" : "Live";
    }
}
