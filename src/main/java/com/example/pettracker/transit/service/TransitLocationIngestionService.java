package com.example.pettracker.transit.service;

import com.example.pettracker.transit.entity.TransitLocation;
import com.example.pettracker.transit.repository.TransitLocationRepository;
import org.springframework.stereotype.Service;

@Service
public class TransitLocationIngestionService {

    private final TransitLocationRepository transitLocationRepository;
    private final TransitGeofencingService transitGeofencingService;

    public TransitLocationIngestionService(
            TransitLocationRepository transitLocationRepository,
            TransitGeofencingService transitGeofencingService) {
        this.transitLocationRepository = transitLocationRepository;
        this.transitGeofencingService = transitGeofencingService;
    }

    public TransitLocation ingest(TransitLocation location) {
        TransitLocation savedLocation = transitLocationRepository.save(location);
        transitGeofencingService.auditLocation(savedLocation.getId());
        return savedLocation;
    }
}
