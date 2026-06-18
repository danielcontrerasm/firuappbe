package com.example.pettracker.controller;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.LocationRequest;
import com.example.pettracker.dto.PetDto;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.PetMapper;
import com.example.pettracker.service.LocationService;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService petService;
    private final UserService userService;
    private final LocationService locationService;

    public PetController(
            PetService petService,
            UserService userService,
            LocationService locationService) {
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
    public List<PetDto> list(Authentication authentication) {
        User user = currentUser(authentication);
        return petService.listForUser(user).stream()
                .map(this::toPetDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetDto> getById(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(toPetDto(pet));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PetDto> createWithImage(
            @RequestPart("pet") PetDto request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = new Pet();
        applyPetFields(pet, request);

        if (user.getRole() == User.Role.ADMIN && request.ownerId() != null) {
            User owner = userService.findById(request.ownerId());
            if (owner == null) {
                throw new RuntimeException("Owner not found");
            }
            pet.setOwner(owner);
        } else {
            pet.setOwner(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toPetDto(petService.create(pet, image)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetDto> update(@PathVariable Long id, @RequestBody PetDto request, Authentication authentication) {
        User user = currentUser(authentication);
        Pet existing = petService.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, existing)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        applyPetFields(existing, request);

        if (user.getRole() == User.Role.ADMIN && request.ownerId() != null) {
            User owner = userService.findById(request.ownerId());
            if (owner == null) {
                throw new RuntimeException("Owner not found");
            }
            existing.setOwner(owner);
        }

        return ResponseEntity.ok(toPetDto(petService.update(existing)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PetDto> updateWithImage(
            @PathVariable Long id,
            @RequestPart("pet") PetDto request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        User user = currentUser(authentication);
        Pet existing = petService.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, existing)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        applyPetFields(existing, request);

        if (user.getRole() == User.Role.ADMIN && request.ownerId() != null) {
            User owner = userService.findById(request.ownerId());
            if (owner == null) {
                throw new RuntimeException("Owner not found");
            }
            existing.setOwner(owner);
        }

        return ResponseEntity.ok(toPetDto(petService.update(existing, image)));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        PetService.PetImage image = petService.findImageById(id);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getRole() != User.Role.ADMIN && !user.getId().equals(image.ownerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imageFileName(image) + "\"")
                .body(image.data());
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

    @GetMapping("/{id}/neighborhood")
    public ResponseEntity<PetNeighborhoodDto> neighborhood(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(id);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(user, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Location latestLocation = locationService.getLatestByPetId(id);
        if (latestLocation == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(locationService.getNeighborhoodByPetId(id));
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

    private PetDto toPetDto(Pet pet) {
        PetDto base = PetMapper.toDto(pet);
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

    private void applyPetFields(Pet pet, PetDto request) {
        pet.setName(request.name());
        pet.setType(request.type());
        pet.setRace(request.race());
        pet.setAge(request.age());
        pet.setWeight(request.weight());
        pet.setImei(request.imei());
        if (request.status() != null) {
            pet.setStatus(Pet.Status.valueOf(request.status().toUpperCase()));
        }
    }

    private String imageFileName(PetService.PetImage image) {
        return image.fileName() == null || image.fileName().isBlank() ? "pet-image" : image.fileName();
    }
}
