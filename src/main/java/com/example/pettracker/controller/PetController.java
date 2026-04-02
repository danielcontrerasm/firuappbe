package com.example.pettracker.controller;

import com.example.pettracker.dto.AlertResponse;
import com.example.pettracker.dto.GeofenceRequest;
import com.example.pettracker.dto.GeofenceResponse;
import com.example.pettracker.dto.LocationCreateRequest;
import com.example.pettracker.dto.LocationResponse;
import com.example.pettracker.dto.LostStatusRequest;
import com.example.pettracker.dto.PetSummaryResponse;
import com.example.pettracker.dto.SearchGroupCreateRequest;
import com.example.pettracker.dto.SearchGroupResponse;
import com.example.pettracker.dto.UpsertPetRequest;
import com.example.pettracker.repository.AlertRepository;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.PetLocationRepository;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.SearchVolunteerRepository;
import com.example.pettracker.service.PetAccessService;
import com.example.pettracker.service.PetMapper;
import com.example.pettracker.service.PetTrackingService;
import jakarta.validation.Valid;
import java.util.List;
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

    private final PetTrackingService petTrackingService;
    private final PetMapper petMapper;
    private final PetAccessService petAccessService;
    private final PetLocationRepository petLocationRepository;
    private final GeofenceRepository geofenceRepository;
    private final AlertRepository alertRepository;
    private final SearchGroupRepository searchGroupRepository;
    private final SearchVolunteerRepository searchVolunteerRepository;

    public PetController(
            PetTrackingService petTrackingService,
            PetMapper petMapper,
            PetAccessService petAccessService,
            PetLocationRepository petLocationRepository,
            GeofenceRepository geofenceRepository,
            AlertRepository alertRepository,
            SearchGroupRepository searchGroupRepository,
            SearchVolunteerRepository searchVolunteerRepository) {
        this.petTrackingService = petTrackingService;
        this.petMapper = petMapper;
        this.petAccessService = petAccessService;
        this.petLocationRepository = petLocationRepository;
        this.geofenceRepository = geofenceRepository;
        this.alertRepository = alertRepository;
        this.searchGroupRepository = searchGroupRepository;
        this.searchVolunteerRepository = searchVolunteerRepository;
    }

    @GetMapping
    public List<PetSummaryResponse> listPets() {
        return petTrackingService.listVisiblePets().stream()
                .map(pet -> petMapper.toPetSummary(
                        pet,
                        petLocationRepository.findTop20ByPetIdOrderByRecordedAtDesc(pet.getId()).stream().findFirst().orElse(null),
                        geofenceRepository.findByPetId(pet.getId()).orElse(null)))
                .toList();
    }

    @PostMapping
    public PetSummaryResponse createPet(@Valid @RequestBody UpsertPetRequest request) {
        var pet = petTrackingService.createPet(request);
        return petMapper.toPetSummary(pet, null, null);
    }

    @PutMapping("/{petId}")
    public PetSummaryResponse updatePet(@PathVariable Long petId, @Valid @RequestBody UpsertPetRequest request) {
        var pet = petTrackingService.updatePet(petId, request);
        return petMapper.toPetSummary(
                pet,
                petLocationRepository.findTop20ByPetIdOrderByRecordedAtDesc(petId).stream().findFirst().orElse(null),
                geofenceRepository.findByPetId(petId).orElse(null));
    }

    @GetMapping("/{petId}")
    public PetSummaryResponse getPet(@PathVariable Long petId) {
        var pet = petAccessService.getAccessiblePet(petId);
        return petMapper.toPetSummary(
                pet,
                petLocationRepository.findTop20ByPetIdOrderByRecordedAtDesc(petId).stream().findFirst().orElse(null),
                geofenceRepository.findByPetId(petId).orElse(null));
    }

    @GetMapping("/{petId}/locations")
    public List<LocationResponse> listLocations(@PathVariable Long petId) {
        petAccessService.getAccessiblePet(petId);
        return petLocationRepository.findByPetIdOrderByRecordedAtDesc(petId).stream()
                .map(petMapper::toLocation)
                .toList();
    }

    @PostMapping("/{petId}/locations")
    public LocationResponse addLocation(@PathVariable Long petId, @Valid @RequestBody LocationCreateRequest request) {
        return petMapper.toLocation(petTrackingService.addLocation(petId, request));
    }

    @PutMapping("/{petId}/geofence")
    public GeofenceResponse upsertGeofence(@PathVariable Long petId, @Valid @RequestBody GeofenceRequest request) {
        return petMapper.toGeofence(petTrackingService.upsertGeofence(petId, request));
    }

    @PutMapping("/{petId}/lost")
    public PetSummaryResponse updateLost(@PathVariable Long petId, @RequestBody LostStatusRequest request) {
        var pet = petTrackingService.updateLostStatus(petId, request.lost());
        return petMapper.toPetSummary(
                pet,
                petLocationRepository.findTop20ByPetIdOrderByRecordedAtDesc(petId).stream().findFirst().orElse(null),
                geofenceRepository.findByPetId(petId).orElse(null));
    }

    @GetMapping("/{petId}/alerts")
    public List<AlertResponse> listAlerts(@PathVariable Long petId) {
        petAccessService.getAccessiblePet(petId);
        return alertRepository.findByPetIdOrderByCreatedAtDesc(petId).stream()
                .map(petMapper::toAlert)
                .toList();
    }

    @GetMapping("/{petId}/search-groups")
    public List<SearchGroupResponse> listSearchGroups(@PathVariable Long petId) {
        petAccessService.getAccessiblePet(petId);
        return searchGroupRepository.findByPetIdOrderByCreatedAtDesc(petId).stream()
                .map(group -> petMapper.toSearchGroup(group, searchVolunteerRepository.findBySearchGroupId(group.getId())))
                .toList();
    }

    @PostMapping("/{petId}/search-groups")
    public SearchGroupResponse createSearchGroup(@PathVariable Long petId, @Valid @RequestBody SearchGroupCreateRequest request) {
        var group = petTrackingService.createSearchGroup(petId, request);
        return petMapper.toSearchGroup(group, List.of());
    }
}
