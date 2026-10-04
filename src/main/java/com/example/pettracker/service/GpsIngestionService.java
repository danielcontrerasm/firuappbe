package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GpsIngestionService {

    private static final long PET_CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long UNKNOWN_IMEI_CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final LocationRepository locationRepository;
    private final PetRepository petRepository;
    private final GeofencingService geofencingService;
    private final Executor gpsExecutor;
    private final AppTimeService appTimeService;
    private final AtomicLong droppedTasks = new AtomicLong();
    private final Map<String, CachedPetLookup> petByImeiCache = new ConcurrentHashMap<>();
    
    public GpsIngestionService(
            LocationRepository locationRepository,
            PetRepository petRepository,
            GeofencingService geofencingService,
            @Qualifier("gpsExecutor") Executor gpsExecutor,
            AppTimeService appTimeService) {
        this.locationRepository = locationRepository;
        this.petRepository = petRepository;
        this.geofencingService = geofencingService;
        this.gpsExecutor = gpsExecutor;
        this.appTimeService = appTimeService;
    }

    @Transactional
    public Location processGpsUpdate(Location l) {
        Location savedLocation = saveToDatabase(l);
        geofencingService.checkAndAlert(savedLocation);
        geofencingService.checkGeofence(savedLocation);
        return savedLocation;
    }

    public void processDecodedLocation(
            String protocol,
            String imei,
            double latitude,
            double longitude,
            Instant instant,
            boolean gpsValid,
            String remoteAddress,
            String context
    ) {
        processDecodedLocation(protocol, imei, latitude, longitude, instant, gpsValid, null, null, remoteAddress, context);
    }

    public void processDecodedLocation(
            String protocol,
            String imei,
            double latitude,
            double longitude,
            Instant instant,
            boolean gpsValid,
            Integer batteryPercent,
            Double batteryVoltage,
            String remoteAddress,
            String context
    ) {
        if (imei == null || imei.isBlank()) {
            log.warn("GPS {} location missing IMEI before async enqueue remote={} context='{}'", protocol, remoteAddress, context);
            return;
        }

        submitGpsTask(protocol + "-" + imei, () -> {
            Optional<CachedPetLookup> cachedPet = findPetByImeiCached(imei);
            if (cachedPet.isEmpty()) {
                log.warn("GPS {} location for unknown IMEI={} remote={} lat={} lon={} context='{}'. Skipping persist.",
                        protocol, imei, remoteAddress, latitude, longitude, context);
                return;
            }

            CachedPetLookup pet = cachedPet.get();
            LocalDateTime timestamp = appTimeService.fromInstant(instant);
            Location location = Location.builder()
                    .pet(Pet.builder().id(pet.petId()).build())
                    .timestamp(timestamp)
                    .latitude(latitude)
                    .longitude(longitude)
                    .batteryPercent(batteryPercent)
                    .batteryVoltage(batteryVoltage)
                    .build();

            Location savedLocation = saveToDatabase(location);
            // Expensive success-path log: fires once per saved GPS point and duplicates location telemetry.
            // log.info("Persisted GPS {} location petId={} petName={} imei={} lat={} lon={} timestamp={} gpsValid={} batteryPercent={} batteryVoltage={} remote={}",
            //         protocol, pet.petId(), pet.petName(), imei, latitude, longitude, timestamp, gpsValid, batteryPercent, batteryVoltage, remoteAddress);
            geofencingService.checkAndAlert(savedLocation);
            geofencingService.checkGeofence(savedLocation);
        });
    }

    public void evictPetCache(String imei) {
        if (imei == null || imei.isBlank()) {
            return;
        }
        petByImeiCache.remove(imei);
        log.info("Evicted GPS pet cache imei={}", imei);
    }

    private Optional<CachedPetLookup> findPetByImeiCached(String imei) {
        long now = System.currentTimeMillis();
        CachedPetLookup cached = petByImeiCache.get(imei);
        if (cached != null && cached.expiresAtMillis() > now) {
            if (cached.petId() == null) {
                log.debug("GPS pet cache hit unknown imei={}", imei);
                return Optional.empty();
            }
            log.debug("GPS pet cache hit imei={} petId={}", imei, cached.petId());
            return Optional.of(cached);
        }

        Optional<Pet> petOpt = petRepository.findByImei(imei);
        if (petOpt.isEmpty()) {
            petByImeiCache.put(imei, new CachedPetLookup(null, null, now + UNKNOWN_IMEI_CACHE_TTL_MILLIS));
            log.info("GPS pet cache miss imei={} result=unknown ttlSeconds={}", imei, UNKNOWN_IMEI_CACHE_TTL_MILLIS / 1000);
            return Optional.empty();
        }

        Pet pet = petOpt.get();
        CachedPetLookup lookup = new CachedPetLookup(pet.getId(), pet.getName(), now + PET_CACHE_TTL_MILLIS);
        petByImeiCache.put(imei, lookup);
        log.info("GPS pet cache miss imei={} result=petId={} petName={} ttlSeconds={}",
                imei, pet.getId(), pet.getName(), PET_CACHE_TTL_MILLIS / 1000);
        return Optional.of(lookup);
    }

    private void submitGpsTask(String taskName, Runnable task) {
        try {
            gpsExecutor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    log.warn("GPS async task failed task={}: {}", taskName, ex.getMessage(), ex);
                }
            });
        } catch (TaskRejectedException ex) {
            droppedTasks.incrementAndGet();
            log.warn("GPS async queue full. Dropping task={}", taskName, ex);
        }
    }

    public long getDroppedTasks() {
        return droppedTasks.get();
    }

    private Location saveToDatabase(Location l) {
        return locationRepository.save(l);
    }

    private record CachedPetLookup(Long petId, String petName, long expiresAtMillis) {
    }
}
