package com.example.pettracker.service;

import com.example.pettracker.dto.GeofenceRequest;
import com.example.pettracker.dto.LocationCreateRequest;
import com.example.pettracker.dto.SearchGroupCreateRequest;
import com.example.pettracker.dto.UpsertPetRequest;
import com.example.pettracker.model.Geofence;
import com.example.pettracker.model.Pet;
import com.example.pettracker.model.PetLocation;
import com.example.pettracker.model.Role;
import com.example.pettracker.model.SearchGroup;
import com.example.pettracker.model.SearchVolunteer;
import com.example.pettracker.model.User;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.PetLocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.SearchVolunteerRepository;
import com.example.pettracker.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PetTrackingService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final PetLocationRepository petLocationRepository;
    private final GeofenceRepository geofenceRepository;
    private final SearchGroupRepository searchGroupRepository;
    private final SearchVolunteerRepository searchVolunteerRepository;
    private final CurrentUserService currentUserService;
    private final PetAccessService petAccessService;
    private final AlertService alertService;

    public PetTrackingService(
            PetRepository petRepository,
            UserRepository userRepository,
            PetLocationRepository petLocationRepository,
            GeofenceRepository geofenceRepository,
            SearchGroupRepository searchGroupRepository,
            SearchVolunteerRepository searchVolunteerRepository,
            CurrentUserService currentUserService,
            PetAccessService petAccessService,
            AlertService alertService) {
        this.petRepository = petRepository;
        this.userRepository = userRepository;
        this.petLocationRepository = petLocationRepository;
        this.geofenceRepository = geofenceRepository;
        this.searchGroupRepository = searchGroupRepository;
        this.searchVolunteerRepository = searchVolunteerRepository;
        this.currentUserService = currentUserService;
        this.petAccessService = petAccessService;
        this.alertService = alertService;
    }

    @Transactional(readOnly = true)
    public List<Pet> listVisiblePets() {
        User currentUser = currentUserService.requireCurrentUser();
        if (currentUserService.isAdmin(currentUser)) {
            return petRepository.findAll();
        }
        return petRepository.findByOwnerId(currentUser.getId());
    }

    public Pet createPet(UpsertPetRequest request) {
        User currentUser = currentUserService.requireCurrentUser();
        User owner = resolveOwner(request.ownerId(), currentUser);

        Pet pet = new Pet();
        pet.setName(request.name());
        pet.setSpecies(request.species());
        pet.setBreed(request.breed());
        pet.setDescription(request.description());
        pet.setOwner(owner);
        pet.setLost(false);
        return petRepository.save(pet);
    }

    public Pet updatePet(Long petId, UpsertPetRequest request) {
        Pet pet = petAccessService.getOwnedPet(petId);
        User currentUser = currentUserService.requireCurrentUser();
        User owner = resolveOwner(request.ownerId(), currentUser);

        pet.setName(request.name());
        pet.setSpecies(request.species());
        pet.setBreed(request.breed());
        pet.setDescription(request.description());
        pet.setOwner(owner);
        return petRepository.save(pet);
    }

    public PetLocation addLocation(Long petId, LocationCreateRequest request) {
        Pet pet = petAccessService.getOwnedPet(petId);

        PetLocation location = new PetLocation();
        location.setPet(pet);
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        location.setSpeed(request.speed());
        location.setAccuracyMeters(request.accuracyMeters());
        location.setRecordedAt(request.recordedAt() == null ? Instant.now() : request.recordedAt());
        PetLocation saved = petLocationRepository.save(location);

        geofenceRepository.findByPetId(petId).ifPresent(geofence -> maybeCreateGeofenceAlert(pet, saved, geofence));
        return saved;
    }

    public Geofence upsertGeofence(Long petId, GeofenceRequest request) {
        Pet pet = petAccessService.getOwnedPet(petId);
        Geofence geofence = geofenceRepository.findByPetId(petId).orElseGet(Geofence::new);
        geofence.setPet(pet);
        geofence.setCenterLatitude(request.centerLatitude());
        geofence.setCenterLongitude(request.centerLongitude());
        geofence.setRadiusMeters(request.radiusMeters());
        return geofenceRepository.save(geofence);
    }

    public Pet updateLostStatus(Long petId, boolean lost) {
        Pet pet = petAccessService.getOwnedPet(petId);
        pet.setLost(lost);
        pet.setLostSince(lost ? Instant.now() : null);
        Pet saved = petRepository.save(pet);
        if (lost) {
            alertService.createLostAlert(saved);
        }
        return saved;
    }

    public SearchGroup createSearchGroup(Long petId, SearchGroupCreateRequest request) {
        Pet pet = petAccessService.getOwnedPet(petId);
        if (!pet.isLost()) {
            throw new IllegalStateException("Search groups can only be created for lost pets");
        }

        SearchGroup group = new SearchGroup();
        group.setPet(pet);
        group.setTitle(request.title());
        group.setNotes(request.notes());
        group.setActive(true);
        group.setCreatedAt(Instant.now());
        return searchGroupRepository.save(group);
    }

    public SearchVolunteer joinSearchGroup(Long searchGroupId) {
        User currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getRoles().contains(Role.ADMIN) || currentUser.getRoles().contains(Role.OWNER) || currentUser.getRoles().contains(Role.VOLUNTEER)) {
            return searchVolunteerRepository.findBySearchGroupIdAndUserId(searchGroupId, currentUser.getId())
                    .orElseGet(() -> {
                        SearchGroup group = searchGroupRepository.findById(searchGroupId)
                                .orElseThrow(() -> new IllegalArgumentException("Search group not found"));
                        SearchVolunteer volunteer = new SearchVolunteer();
                        volunteer.setSearchGroup(group);
                        volunteer.setUser(currentUser);
                        volunteer.setJoinedAt(Instant.now());
                        return searchVolunteerRepository.save(volunteer);
                    });
        }

        throw new AccessDeniedException("You cannot join search groups");
    }

    private User resolveOwner(Long ownerId, User currentUser) {
        if (ownerId == null || !currentUserService.isAdmin(currentUser)) {
            return currentUser;
        }
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
    }

    private void maybeCreateGeofenceAlert(Pet pet, PetLocation location, Geofence geofence) {
        double distanceMeters = haversineMeters(
                geofence.getCenterLatitude(),
                geofence.getCenterLongitude(),
                location.getLatitude(),
                location.getLongitude());
        if (distanceMeters > geofence.getRadiusMeters()) {
            alertService.createGeofenceAlert(pet, location, geofence, distanceMeters);
        }
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
