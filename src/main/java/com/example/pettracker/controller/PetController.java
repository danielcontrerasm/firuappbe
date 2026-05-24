package com.example.pettracker.controller;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.LocationRequest;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.service.LocationService;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService petService;
    private final UserService userService;
    private final LocationService locationService;

    public PetController(PetService petService, UserService userService, LocationService locationService) {
        this.petService = petService;
        this.userService = userService;
        this.locationService = locationService;
    }
    @GetMapping("/locations")
    public List <LocationDTO> findLastLocationsByUserId ( Authentication authentication){
        User user = currentUser(authentication);
        return locationService.findLastLocationsByUserId(user.getId());
    }

    @GetMapping
    public List<Pet> list(Authentication authentication) {
        User user = currentUser(authentication);
        return petService.listForUser(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getById(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping
    public ResponseEntity<Pet> create(@RequestBody Pet pet, Authentication authentication) {
        User user = currentUser(authentication);
        if (pet.getOwner() == null || pet.getOwner().getId() == null || user.getRole() != User.Role.ADMIN) {
            pet.setOwner(user);
        } else {
            User owner = userService.findById(pet.getOwner().getId());
            if (owner == null) {
                throw new RuntimeException("Owner not found");
            }
            pet.setOwner(owner);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(petService.create(pet));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pet> update(@PathVariable Long id, @RequestBody Pet request, Authentication authentication) {
        User user = currentUser(authentication);
        Pet existing = petService.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, existing)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        existing.setName(request.getName());
        existing.setType(request.getType());
        existing.setImei(request.getImei());
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }

        if (user.getRole() == User.Role.ADMIN && request.getOwner() != null && request.getOwner().getId() != null) {
            User owner = userService.findById(request.getOwner().getId());
            if (owner == null) {
                throw new RuntimeException("Owner not found");
            }
            existing.setOwner(owner);
        }

        return ResponseEntity.ok(petService.update(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        petService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lost")
    public ResponseEntity<Pet> markLost(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(petService.markPetAsLost(id));
    }

    @PostMapping("/{id}/found")
    public ResponseEntity<Pet> markFound(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(petService.markPetAsFound(id));
    }

    @GetMapping("/{id}/locations")
    public ResponseEntity<List<LocationDTO>> locations(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(locationService.getByPetId(id));
    }

    @GetMapping("/{id}/route")
    public ResponseEntity<List<LocationDTO>> routeLast3Hours(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(locationService.getPetRouteLast3Hours(id));
    }

    @PostMapping("/{id}/locations")
    public ResponseEntity<LocationDTO> addLocation(
            @PathVariable Long id,
            @RequestBody LocationRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Location location = Location.builder()
                .pet(pet)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timestamp(request.getTimestamp() == null ? LocalDateTime.now() : request.getTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.save(location));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private boolean canAccessPet(User user, Pet pet) {
        return user.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null && pet.getOwner().getId() != null && pet.getOwner().getId().equals(user.getId()));
    }
}
