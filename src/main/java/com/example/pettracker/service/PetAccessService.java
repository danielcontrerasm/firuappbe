package com.example.pettracker.service;

import com.example.pettracker.model.Pet;
import com.example.pettracker.model.User;
import com.example.pettracker.repository.PetRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class PetAccessService {

    private final PetRepository petRepository;
    private final CurrentUserService currentUserService;

    public PetAccessService(PetRepository petRepository, CurrentUserService currentUserService) {
        this.petRepository = petRepository;
        this.currentUserService = currentUserService;
    }

    public Pet getAccessiblePet(Long petId) {
        User user = currentUserService.requireCurrentUser();
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("Pet not found: " + petId));

        if (currentUserService.isAdmin(user) || pet.getOwner().getId().equals(user.getId())) {
            return pet;
        }

        throw new AccessDeniedException("You cannot access this pet");
    }

    public Pet getOwnedPet(Long petId) {
        User user = currentUserService.requireCurrentUser();
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("Pet not found: " + petId));

        if (currentUserService.isAdmin(user) || pet.getOwner().getId().equals(user.getId())) {
            return pet;
        }

        throw new AccessDeniedException("You cannot modify this pet");
    }
}
