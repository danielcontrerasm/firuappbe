package com.example.pettracker.service;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.entity.Location;
import com.example.pettracker.mapper.LocationMapper;
import com.example.pettracker.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final GpsIngestionService gpsIngestionService;
    private final LocationMapper locationMapper;
    private final NeighborhoodLookupService neighborhoodLookupService;
    public LocationService(
            LocationRepository locationRepository,
            GpsIngestionService gpsIngestionService,
            LocationMapper locationMapper,
            NeighborhoodLookupService neighborhoodLookupService) {
        this.locationRepository = locationRepository;
        this.gpsIngestionService = gpsIngestionService;
        this.locationMapper = locationMapper;
        this.neighborhoodLookupService = neighborhoodLookupService;
    }

    public LocationDTO save(Location l) {
       return  locationMapper.toDto(gpsIngestionService.processGpsUpdate(l));
    }

    public List<LocationDTO> getByPetId(Long petId) {
        return locationRepository.findFirstByPetIdOrderByTimestampDesc(petId).stream().map(locationMapper::toDto)
                .toList();

    }

    public Location getLatestByPetId(Long petId) {
        return locationRepository.findTopByPetIdOrderByTimestampDesc(petId).orElse(null);
    }

    public PetNeighborhoodDto getNeighborhoodByPetId(Long petId) {
        Location latestLocation = getLatestByPetId(petId);
        if (latestLocation == null) {
            return null;
        }
        return neighborhoodLookupService.resolveNeighborhood(latestLocation);
    }

    public List<LocationDTO> findAll() {
        return locationRepository.findLastLocationsForAllPets()
                .stream()
                .map(locationMapper::toDto)
                .toList();
    }

    public List<LocationDTO> findLastLocationsByUserId(Long userId) {
        return locationRepository.findLastLocationsByUserId(userId)
                .stream()
                .map(locationMapper::toDto)
                .toList();
    }

    public List<LocationDTO> getPetRouteLast3Hours(Long petId) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(3);
        return locationRepository.findPetRouteLast3Hours(petId, cutoff)
                .stream()
                .map(locationMapper::toDto)
                .toList();
    }

}
