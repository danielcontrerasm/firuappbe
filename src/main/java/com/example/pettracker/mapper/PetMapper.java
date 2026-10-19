package com.example.pettracker.mapper;

import com.example.pettracker.dto.PetDto;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.service.LocationService;
import org.springframework.stereotype.Component;

@Component
public class PetMapper {

    private final LocationService locationService;

    public PetMapper(LocationService locationService) {
        this.locationService = locationService;
    }

    public PetDto toDto(Pet pet) {
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
                pet.getTerminalId(),
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

    public PetDto toDtoWithNeighborhood(Pet pet) {
        PetDto base = toDto(pet);
        PetNeighborhoodDto neighborhood = locationService.getNeighborhoodByPetId(pet.getId());

        return new PetDto(
                base.id(),
                base.name(),
                base.type(),
                base.race(),
                base.age(),
                base.weight(),
                base.status(),
                base.statusLabel(),
                base.imei(),
                base.terminalId(),
                base.createdAt(),
                base.ownerId(),
                base.ownerName(),
                base.ownerEmail(),
                base.imageUrl(),
                neighborhood == null ? null : neighborhood.city(),
                neighborhood == null ? null : neighborhood.district(),
                neighborhood == null ? null : neighborhood.neighborhood()
        );
    }

    public void applyToEntity(Pet pet, PetDto request) {
        pet.setName(request.name());
        pet.setType(request.type());
        pet.setRace(request.race());
        pet.setAge(request.age());
        pet.setWeight(request.weight());
        pet.setImei(request.imei());
        pet.setTerminalId(request.terminalId());
        if (request.status() != null) {
            pet.setStatus(Pet.Status.valueOf(request.status().toUpperCase()));
        }
    }

    private static String statusLabel(Pet pet) {
        if (pet.getStatus() == null) {
            return null;
        }
        return pet.getStatus() == Pet.Status.LOST ? "Lost" : "Live";
    }
}
