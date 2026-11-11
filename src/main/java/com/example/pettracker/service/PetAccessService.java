package com.example.pettracker.service;

import com.example.pettracker.dto.PetShareDtos.ShareRequest;
import com.example.pettracker.dto.PetShareDtos.SharedPerson;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.PetAccess;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetAccessRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PetAccessService {
    private final PetAccessRepository petAccessRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final TemporaryPetAccessService temporaryPetAccessService;

    public PetAccessService(
            PetAccessRepository petAccessRepository,
            PetRepository petRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            TemporaryPetAccessService temporaryPetAccessService) {
        this.petAccessRepository = petAccessRepository;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.temporaryPetAccessService = temporaryPetAccessService;
    }

    public SharedPerson share(Long petId, ShareRequest request, Authentication authentication) {
        User owner = currentUserService.require(authentication);
        Pet pet = requirePet(petId);
        requireOwnerOrAdmin(owner, pet);
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User recipient = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (pet.getOwner() != null && recipient.getId().equals(pet.getOwner().getId())) {
            throw new IllegalArgumentException("El dueño ya tiene acceso a la mascota");
        }

        PetAccess existing = petAccessRepository.findByPetIdAndUserId(petId, recipient.getId()).orElse(null);
        if (existing != null) {
            return toDto(existing);
        }
        long sharedCount = petAccessRepository.countByPetId(petId);
        boolean unlimitedSharing = owner.getRole() == User.Role.ADMIN
                || PlanPolicy.planOf(owner) == User.Plan.PREMIUM;
        if (!unlimitedSharing && sharedCount >= PlanPolicy.authorizedPeoplePerPet(owner)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tu plan permite compartir cada mascota con hasta "
                            + PlanPolicy.authorizedPeoplePerPet(owner) + " personas");
        }

        PetAccess saved = petAccessRepository.save(PetAccess.builder()
                .pet(pet)
                .user(recipient)
                .createdAt(Instant.now())
                .build());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SharedPerson> list(Long petId, Authentication authentication) {
        User current = currentUserService.require(authentication);
        Pet pet = requirePet(petId);
        requireOwnerOrAdmin(current, pet);
        return petAccessRepository.findByPetIdOrderByCreatedAtAsc(petId).stream()
                .map(this::toDto)
                .toList();
    }

    public void revoke(Long petId, Long userId, Authentication authentication) {
        User current = currentUserService.require(authentication);
        Pet pet = requirePet(petId);
        requireOwnerOrAdmin(current, pet);
        petAccessRepository.findByPetIdAndUserId(petId, userId)
                .ifPresent(petAccessRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean canAccess(User user, Pet pet) {
        if (user == null || pet == null) return false;
        if (user.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null && user.getId().equals(pet.getOwner().getId()))) {
            return true;
        }
        return user.getId() != null
                && pet.getId() != null
                && (petAccessRepository.findByPetIdAndUserId(pet.getId(), user.getId()).isPresent()
                || temporaryPetAccessService.canAccess(user, pet));
    }

    @Transactional(readOnly = true)
    public boolean canViewLocation(User user, Pet pet) {
        if (isOwnerOrAdmin(user, pet)) return true;
        return canAccessPermanent(user, pet) || temporaryPetAccessService.canViewLocation(user, pet);
    }

    @Transactional(readOnly = true)
    public boolean canViewHistory(User user, Pet pet) {
        if (isOwnerOrAdmin(user, pet)) return true;
        return canAccessPermanent(user, pet) || temporaryPetAccessService.canViewHistory(user, pet);
    }

    @Transactional(readOnly = true)
    public List<Pet> currentlyAccessibleTemporaryPets(User user) {
        return temporaryPetAccessService.currentlyAccessiblePets(user);
    }

    private boolean canAccessPermanent(User user, Pet pet) {
        return user != null && pet != null && user.getId() != null && pet.getId() != null
                && petAccessRepository.findByPetIdAndUserId(pet.getId(), user.getId()).isPresent();
    }

    private boolean isOwnerOrAdmin(User user, Pet pet) {
        return user != null && pet != null
                && (user.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null && user.getId().equals(pet.getOwner().getId())));
    }

    private Pet requirePet(Long petId) {
        return petRepository.findById(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));
    }

    private void requireOwnerOrAdmin(User current, Pet pet) {
        boolean owner = pet.getOwner() != null && current.getId().equals(pet.getOwner().getId());
        if (current.getRole() != User.Role.ADMIN && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el dueño puede administrar accesos");
        }
    }

    private SharedPerson toDto(PetAccess access) {
        User user = access.getUser();
        return new SharedPerson(
                user == null ? null : user.getId(),
                user == null ? null : user.getName(),
                user == null ? null : user.getEmail(),
                access.getCreatedAt());
    }
}
