package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Set;
import java.util.List;

@Service
public class PetService {
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final PetRepository petRepository;
    @Autowired
    private LostPetNotificationService lostPetNotificationService;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public Pet create(Pet p) {
        return petRepository.save(p);
    }

    public Pet create(Pet pet, MultipartFile image) {
        setImageIfPresent(pet, image);
        return petRepository.save(pet);
    }

    public List<Pet> listForUser(User user) {
        if (user.getRole() == User.Role.ADMIN) return petRepository.findAll();
        return petRepository.findByOwnerId(user.getId());
    }

    public Pet findById(Long id) {
        return petRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public PetImage findImageById(Long id) {
        Pet pet = petRepository.findById(id).orElse(null);
        if (pet == null || pet.getImageData() == null || pet.getImageContentType() == null) {
            return null;
        }
        return new PetImage(
                pet.getOwner() == null ? null : pet.getOwner().getId(),
                pet.getImageData(),
                pet.getImageContentType(),
                pet.getImageFileName()
        );
    }

    public Pet update(Pet p) {
        return petRepository.save(p);
    }

    public Pet update(Pet pet, MultipartFile image) {
        setImageIfPresent(pet, image);
        return petRepository.save(pet);
    }

    private void setImageIfPresent(Pet pet, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        validateImage(image);
        try {
            pet.setImageContentType(image.getContentType());
            pet.setImageFileName(image.getOriginalFilename());
            pet.setImageData(image.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Could not read pet image", e);
        }
    }

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Pet image must be JPEG, PNG, WebP, or GIF");
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Pet image must be 5MB or smaller");
        }
    }

    public record PetImage(Long ownerId, byte[] data, String contentType, String fileName) {
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
