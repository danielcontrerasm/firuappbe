package com.example.pettracker.service;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.LocationRequest;
import com.example.pettracker.dto.PetDto;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.dto.PetWalkDistanceDayDto;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.PetMapper;
import com.example.pettracker.repository.PetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Set;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PetService {
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final PetRepository petRepository;
    private final GpsIngestionService gpsIngestionService;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final LocationService locationService;
    private final PetWalkDistanceService petWalkDistanceService;
    private final PetMapper petMapper;
    @Autowired
    private LostPetNotificationService lostPetNotificationService;

    public PetService(
            PetRepository petRepository,
            GpsIngestionService gpsIngestionService,
            CurrentUserService currentUserService,
            UserService userService,
            LocationService locationService,
            PetWalkDistanceService petWalkDistanceService,
            PetMapper petMapper) {
        this.petRepository = petRepository;
        this.gpsIngestionService = gpsIngestionService;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.locationService = locationService;
        this.petWalkDistanceService = petWalkDistanceService;
        this.petMapper = petMapper;
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> findLastLocationsForCurrentUser(Authentication authentication) {
        User user = currentUserService.require(authentication);
        return locationService.findLastLocationsByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<PetDto> listForCurrentUser(Authentication authentication, int page, int size) {
        User user = currentUserService.require(authentication);
        return listForUser(user, page, size).stream()
                .map(petMapper::toDtoWithNeighborhood)
                .toList();
    }

    @Transactional(readOnly = true)
    public PetDto getForCurrentUser(Long id, Authentication authentication) {
        return petMapper.toDtoWithNeighborhood(requireAccessiblePet(id, authentication));
    }

    @Transactional
    public PetDto create(PetDto request, MultipartFile image, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pet pet = new Pet();
        petMapper.applyToEntity(pet, request);
        pet.setOwner(resolveOwner(user, request.ownerId()));

        Pet saved = image == null ? create(pet) : create(pet, image);
        return petMapper.toDtoWithNeighborhood(saved);
    }

    @Transactional
    public PetDto update(Long id, PetDto request, MultipartFile image, Authentication authentication) {
        Pet existing = requireAccessiblePet(id, authentication);
        User user = currentUserService.require(authentication);
        petMapper.applyToEntity(existing, request);
        if (user.getRole() == User.Role.ADMIN && request.ownerId() != null) {
            existing.setOwner(resolveOwner(user, request.ownerId()));
        }

        Pet saved = image == null ? update(existing) : update(existing, image);
        return petMapper.toDtoWithNeighborhood(saved);
    }

    @Transactional(readOnly = true)
    public PetImage findAccessibleImageById(Long id, Authentication authentication) {
        User user = currentUserService.require(authentication);
        PetImage image = findImageById(id);
        if (image == null) {
            throw notFound("Pet image not found");
        }
        if (user.getRole() != User.Role.ADMIN && !Objects.equals(user.getId(), image.ownerId())) {
            throw forbidden();
        }
        return image;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        delete(id);
    }

    @Transactional
    public PetDto markPetAsLost(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return petMapper.toDtoWithNeighborhood(markPetAsLost(id));
    }

    @Transactional
    public PetDto markPetAsFound(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return petMapper.toDtoWithNeighborhood(markPetAsFound(id));
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getLocations(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return locationService.getByPetId(id);
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getRouteLast3Hours(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return locationService.getPetRouteLast3Hours(id);
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getRouteBetween(Long id, String from, String to, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return locationService.getPetRouteBetween(id, from, to);
    }

    @Transactional
    public PetNeighborhoodDto getNeighborhood(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        Location latestLocation = locationService.getLatestByPetId(id);
        if (latestLocation == null) {
            throw notFound("Pet location not found");
        }
        return locationService.getNeighborhoodByPetId(id);
    }

    @Transactional(readOnly = true)
    public List<PetWalkDistanceDayDto> getWeeklyWalkDistance(Long id, LocalDate weekStart, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return petWalkDistanceService.getWeeklyDistance(id, weekStart);
    }

    @Transactional
    public LocationDTO addLocation(Long id, LocationRequest request, Authentication authentication) {
        Pet pet = requireAccessiblePet(id, authentication);
        LocalDateTime timestamp = locationService.parseRequestTimestamp(request.getTimestamp());
        Location location = Location.builder()
                .pet(pet)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timestamp(timestamp)
                .batteryPercent(request.getBatteryPercent())
                .batteryVoltage(request.getBatteryVoltage())
                .build();

        return locationService.save(location);
    }

    public Pet create(Pet p) {
        Pet saved = petRepository.save(p);
        gpsIngestionService.evictPetCache(saved.getTerminalId());
        return saved;
    }

    public Pet create(Pet pet, MultipartFile image) {
        setImageIfPresent(pet, image);
        Pet saved = petRepository.save(pet);
        gpsIngestionService.evictPetCache(saved.getTerminalId());
        return saved;
    }

    public List<Pet> listForUser(User user) {
        if (user.getRole() == User.Role.ADMIN) return petRepository.findAll();
        return petRepository.findByOwnerId(user.getId());
    }

    public List<Pet> listForUser(User user, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        if (user.getRole() == User.Role.ADMIN) {
            return petRepository.findAll(pageable).getContent();
        }
        return petRepository.findByOwnerId(user.getId(), pageable);
    }

    private Pageable pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));
    }

    private Pet requireAccessiblePet(Long id, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pet pet = petRepository.findById(id).orElseThrow(() -> notFound("Pet not found"));
        if (!canAccessPet(user, pet)) {
            throw forbidden();
        }
        return pet;
    }

    private boolean canAccessPet(User user, Pet pet) {
        return user.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null
                && pet.getOwner().getId() != null
                && pet.getOwner().getId().equals(user.getId()));
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        if (currentUser.getRole() == User.Role.ADMIN && ownerId != null) {
            User owner = userService.findById(ownerId);
            if (owner == null) {
                throw notFound("Owner not found");
            }
            return owner;
        }
        return currentUser;
    }

    private ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this pet");
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
        String previousTerminalId = findPreviousTerminalId(p);
        Pet saved = petRepository.save(p);
        evictTerminalIdCache(previousTerminalId, saved.getTerminalId());
        return saved;
    }

    public Pet update(Pet pet, MultipartFile image) {
        String previousTerminalId = findPreviousTerminalId(pet);
        setImageIfPresent(pet, image);
        Pet saved = petRepository.save(pet);
        evictTerminalIdCache(previousTerminalId, saved.getTerminalId());
        return saved;
    }

    private String findPreviousTerminalId(Pet pet) {
        if (pet == null || pet.getId() == null) {
            return null;
        }
        return petRepository.findById(pet.getId())
                .map(Pet::getTerminalId)
                .orElse(null);
    }

    private void evictTerminalIdCache(String previousTerminalId, String currentTerminalId) {
        gpsIngestionService.evictPetCache(previousTerminalId);
        if (currentTerminalId == null || currentTerminalId.equals(previousTerminalId)) {
            return;
        }
        gpsIngestionService.evictPetCache(currentTerminalId);
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
        public String fileNameOrDefault() {
            return fileName == null || fileName.isBlank() ? "pet-image" : fileName;
        }
    }

    public void delete(Long id) {
        Pet pet = petRepository.findById(id).orElse(null);
        petRepository.deleteById(id);
        if (pet != null) {
            gpsIngestionService.evictPetCache(pet.getTerminalId());
        }
    }
    // ... existing code ...


    public Pet markPetAsLost(Long petId) {
        log.info("Entered markPetAsLost in PetService with petId: {}", petId);
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setStatus(Pet.Status.LOST);
        Pet updatedPet = petRepository.save(pet);

        // Send notifications to owner
        lostPetNotificationService.notifyPetLost(updatedPet.getId());

        return updatedPet;
    }

    public Pet markPetAsFound(Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setStatus(Pet.Status.ACTIVE);
        Pet updatedPet = petRepository.save(pet);

        // Send notifications to owner
        lostPetNotificationService.notifyPetFound(updatedPet.getId());

        return updatedPet;
    }

// ... existing code ...


}
