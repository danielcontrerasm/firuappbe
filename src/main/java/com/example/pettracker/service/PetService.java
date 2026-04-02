package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PetService {
    private final PetRepository petRepository;
    @Autowired
    private LostPetNotificationService lostPetNotificationService;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public Pet create(Pet p) {
        return petRepository.save(p);
    }

    public List<Pet> listForUser(User user) {
        if (user.getRole() == User.Role.ADMIN) return petRepository.findAll();
        return petRepository.findByOwnerId(user.getId());
    }

    public Pet findById(Long id) {
        return petRepository.findById(id).orElse(null);
    }

    public Pet update(Pet p) {
        return petRepository.save(p);
    }

    public void delete(Long id) {
        petRepository.deleteById(id);
    }
    // ... existing code ...


    public Pet markPetAsLost(Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setStatus(Pet.Status.LOST);
        Pet updatedPet = petRepository.save(pet);

        // Send notifications to owner
        lostPetNotificationService.notifyPetLost(updatedPet);

        return updatedPet;
    }

    public Pet markPetAsFound(Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setStatus(Pet.Status.ACTIVE);
        Pet updatedPet = petRepository.save(pet);

        // Send notifications to owner
        lostPetNotificationService.notifyPetFound(updatedPet);

        return updatedPet;
    }

// ... existing code ...


}
