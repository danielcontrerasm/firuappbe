package com.example.pettracker.service;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.entity.Location;
import com.example.pettracker.mapper.LocationMapper;
import com.example.pettracker.repository.LocationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class LocationService {
    private static final int DEFAULT_RECENT_LOCATION_LIMIT = 1;
    private static final int DEFAULT_ROUTE_LIMIT = 720;

    private final LocationRepository locationRepository;
    private final GpsIngestionService gpsIngestionService;
    private final LocationMapper locationMapper;
    private final NeighborhoodLookupService neighborhoodLookupService;
    private final AppTimeService appTimeService;

    public LocationService(
            LocationRepository locationRepository,
            GpsIngestionService gpsIngestionService,
            LocationMapper locationMapper,
            NeighborhoodLookupService neighborhoodLookupService,
            AppTimeService appTimeService) {
        this.locationRepository = locationRepository;
        this.gpsIngestionService = gpsIngestionService;
        this.locationMapper = locationMapper;
        this.neighborhoodLookupService = neighborhoodLookupService;
        this.appTimeService = appTimeService;
    }

    public LocationDTO save(Location l) {
        Location savedLocation = gpsIngestionService.processGpsUpdate(l);
        return toDtoWithNeighborhood(savedLocation);
    }

    @Transactional
    public List<LocationDTO> getByPetId(Long petId) {
        return locationRepository.findByPetIdOrderByTimestampDesc(
                        petId,
                        PageRequest.of(0, DEFAULT_RECENT_LOCATION_LIMIT)
                )
                .stream()
                .map(this::toDtoWithNeighborhood)
                .toList();

    }

    @Transactional(readOnly = true)
    public Location getLatestByPetId(Long petId) {
        return locationRepository.findTopByPetIdOrderByTimestampDesc(petId).orElse(null);
    }

    @Transactional
    public PetNeighborhoodDto getNeighborhoodByPetId(Long petId) {
        Location latestLocation = getLatestByPetId(petId);
        if (latestLocation == null) {
            return null;
        }
        Location enrichedLocation = enrichWithNeighborhood(latestLocation);
        return toPetNeighborhoodDto(enrichedLocation);
    }

    @Transactional
    public List<LocationDTO> findAll() {
        return locationRepository.findLastLocationsForAllPets()
                .stream()
                .map(this::toDtoWithNeighborhood)
                .toList();
    }

    @Transactional
    public List<LocationDTO> findLastLocationsByUserId(Long userId) {
        return locationRepository.findLastLocationsByUserId(userId)
                .stream()
                .map(this::toDtoWithNeighborhood)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getPetRouteLast3Hours(Long petId) {
        LocalDateTime cutoff = appTimeService.now().minusHours(3);
        return locationRepository.findPetRouteSince(
                        petId,
                        cutoff,
                        PageRequest.of(0, DEFAULT_ROUTE_LIMIT)
                )
                .stream()
                .map(locationMapper::toDto)
                .toList();
    }

    public LocalDateTime now() {
        return appTimeService.now();
    }

    public LocalDateTime parseRequestTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return appTimeService.now();
        }

        try {
            return appTimeService.fromInstant(Instant.parse(timestamp));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return appTimeService.fromInstant(OffsetDateTime.parse(timestamp).toInstant());
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid timestamp. Use ISO-8601 format.", exception);
        }
    }

    private LocationDTO toDtoWithNeighborhood(Location location) {
        Location enrichedLocation = enrichWithNeighborhood(location);
        return locationMapper.toDto(enrichedLocation);
    }

    private PetNeighborhoodDto toPetNeighborhoodDto(Location location) {
        if (location == null) {
            return null;
        }
        return new PetNeighborhoodDto(
                location.getPet() == null ? null : location.getPet().getId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp() == null ? null : location.getTimestamp().toString(),
                location.getNeighborhood(),
                location.getComuna(),
                location.getCity(),
                location.getAddress(),
                location.getNeighborhoodSource(),
                Boolean.TRUE.equals(location.getNeighborhoodResolved())
        );
    }

    private Location enrichWithNeighborhood(Location location) {
        if (location == null || hasStoredNeighborhoodData(location)) {
            return location;
        }

        PetNeighborhoodDto neighborhood = neighborhoodLookupService.resolveNeighborhood(location);
        applyNeighborhood(location, neighborhood);
        if (location.getId() != null) {
            return locationRepository.save(location);
        }
        return location;
    }

    private boolean hasStoredNeighborhoodData(Location location) {
        return hasText(location.getCity())
                || hasText(location.getAddress())
                || hasText(location.getNeighborhood())
                || hasText(location.getComuna())
                || location.getNeighborhoodResolved() != null
                || hasText(location.getNeighborhoodSource());
    }

    private void applyNeighborhood(Location location, PetNeighborhoodDto neighborhood) {
        if (location == null || neighborhood == null) {
            return;
        }
        location.setCity(neighborhood.city());
        location.setAddress(neighborhood.displayName());
        location.setNeighborhood(neighborhood.neighborhood());
        location.setComuna(neighborhood.district());
        location.setNeighborhoodResolved(neighborhood.resolved());
        location.setNeighborhoodSource(neighborhood.source());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
