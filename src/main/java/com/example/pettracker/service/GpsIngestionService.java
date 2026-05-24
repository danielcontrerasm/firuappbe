package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
// Self explanatory code
// avoid magic numbers
// use descriptive booleans
// use meaningful names
// avoid deep nesting
// one function one responsibility
// KISS DRY
// use proper name methods and
// use final for constants
// make small methods

@Service
public class GpsIngestionService {

    private final LocationRepository locationRepository;
    private final GeofencingService geofencingService;
    
    public GpsIngestionService(LocationRepository locationRepository, GeofencingService geofencingService) {
        this.locationRepository = locationRepository;
        this.geofencingService = geofencingService;
    }
    private final ExecutorService gpsExecutor = new ThreadPoolExecutor(
        4, 8, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public Location processGpsUpdate(Location l) {
        gpsExecutor.submit(() -> {
            Location savedLocation = saveToDatabase(l);
            geofencingService.checkAndAlert(savedLocation);
            geofencingService.checkGeofence(savedLocation);
        });
        return l;
    }

    private Location saveToDatabase(Location l) {
        return locationRepository.save(l);
    }
}
