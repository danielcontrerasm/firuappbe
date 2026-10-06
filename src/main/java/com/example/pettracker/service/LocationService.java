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
        PetNeighborhoodDto neighborhood = neighborhoodLookupService.resolveNeighborhood(savedLocation);
        return locationMapper.toDto(savedLocation, neighborhood);
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getByPetId(Long petId) {
        return locationRepository.findByPetIdOrderByTimestampDesc(
                        petId,
                        PageRequest.of(0, DEFAULT_RECENT_LOCATION_LIMIT)
                )
                .stream()
                .map(locationMapper::toDto)
                .toList();

    }

    @Transactional(readOnly = true)
    public Location getLatestByPetId(Long petId) {
        return locationRepository.findTopByPetIdOrderByTimestampDesc(petId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PetNeighborhoodDto getNeighborhoodByPetId(Long petId) {
        Location latestLocation = getLatestByPetId(petId);
        if (latestLocation == null) {
            return null;
        }
        return neighborhoodLookupService.resolveNeighborhood(latestLocation);
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> findAll() {
        return locationRepository.findLastLocationsForAllPets()
                .stream()
                .map(locationMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> findLastLocationsByUserId(Long userId) {
        return locationRepository.findLastLocationsByUserId(userId)
                .stream()
                .map(locationMapper::toDto)
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

}
