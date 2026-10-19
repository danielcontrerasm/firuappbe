package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GpsIngestionService {

    private final LocationRepository locationRepository;
    private final PetRepository petRepository;
    private final GeofencingService geofencingService;
    private final Executor gpsExecutor;
    private final AppTimeService appTimeService;
    private final Cache<String, CachedPetLookup> petByTerminalIdCache;
    private final Duration knownTerminalIdCacheTtl;
    private final Duration unknownTerminalIdCacheTtl;
    private final boolean messageQueueEnabled;
    private final int messageQueueConsumers;
    private final BlockingQueue<GpsLocationMessage> messageQueue;
    private final AtomicBoolean messageQueueRunning = new AtomicBoolean();
    private final List<Thread> messageQueueWorkers = new ArrayList<>();
    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicLong enqueuedMessages = new AtomicLong();
    private final AtomicLong droppedMessages = new AtomicLong();
    private final AtomicLong processedQueuedMessages = new AtomicLong();
    
    public GpsIngestionService(
            LocationRepository locationRepository,
            PetRepository petRepository,
            GeofencingService geofencingService,
            @Qualifier("gpsExecutor") Executor gpsExecutor,
            AppTimeService appTimeService,
            @Qualifier("gpsPetByTerminalIdCache") Cache<String, CachedPetLookup> petByTerminalIdCache,
            @Value("${gps.cache.pet-by-terminal-id.known-ttl:PT24H}") Duration knownTerminalIdCacheTtl,
            @Value("${gps.cache.pet-by-terminal-id.unknown-ttl:PT1M}") Duration unknownTerminalIdCacheTtl,
            @Value("${gps.ingestion.message-queue.enabled:false}") boolean messageQueueEnabled,
            @Value("${gps.ingestion.message-queue.capacity:10000}") int messageQueueCapacity,
            @Value("${gps.ingestion.message-queue.consumers:1}") int messageQueueConsumers) {
        this.locationRepository = locationRepository;
        this.petRepository = petRepository;
        this.geofencingService = geofencingService;
        this.gpsExecutor = gpsExecutor;
        this.appTimeService = appTimeService;
        this.petByTerminalIdCache = petByTerminalIdCache;
        this.knownTerminalIdCacheTtl = knownTerminalIdCacheTtl;
        this.unknownTerminalIdCacheTtl = unknownTerminalIdCacheTtl;
        this.messageQueueEnabled = messageQueueEnabled;
        this.messageQueueConsumers = Math.max(1, messageQueueConsumers);
        this.messageQueue = new LinkedBlockingQueue<>(Math.max(1, messageQueueCapacity));
    }

    @PostConstruct
    void startMessageQueue() {
        if (!messageQueueEnabled) {
            log.info("GPS ingestion message queue is disabled; decoded locations use direct async persistence.");
            return;
        }

        messageQueueRunning.set(true);
        for (int i = 0; i < messageQueueConsumers; i++) {
            Thread worker = new Thread(this::consumeQueuedMessages, "GpsMessageQueue-" + (i + 1));
            worker.setDaemon(true);
            worker.start();
            messageQueueWorkers.add(worker);
        }
        log.info("GPS ingestion message queue enabled capacity={} consumers={}", messageQueue.remainingCapacity(), messageQueueConsumers);
    }

    @PreDestroy
    void stopMessageQueue() {
        messageQueueRunning.set(false);
        messageQueueWorkers.forEach(Thread::interrupt);
    }

    @Transactional
    public Location processGpsUpdate(Location l) {
        Location savedLocation = saveToDatabase(l);
        geofencingService.checkGeofence(savedLocation);
        return savedLocation;
    }

    public void processDecodedLocation(
            String protocol,
            String terminalId,
            double latitude,
            double longitude,
            Instant instant,
            boolean gpsValid,
            String remoteAddress,
            String context
    ) {
        processDecodedLocation(protocol, terminalId, latitude, longitude, instant, gpsValid, null, null, remoteAddress, context);
    }

    public void processDecodedLocation(
            String protocol,
            String terminalId,
            double latitude,
            double longitude,
            Instant instant,
            boolean gpsValid,
            Integer batteryPercent,
            Double batteryVoltage,
            String remoteAddress,
            String context
    ) {
        if (terminalId == null || terminalId.isBlank()) {
            log.warn("GPS {} location missing terminalId before async enqueue remote={} context='{}'", protocol, remoteAddress, context);
            return;
        }

        GpsLocationMessage message = new GpsLocationMessage(
                protocol,
                terminalId,
                latitude,
                longitude,
                instant,
                gpsValid,
                batteryPercent,
                batteryVoltage,
                remoteAddress,
                context
        );

        if (messageQueueEnabled) {
            enqueueMessage(message);
            return;
        }

        submitGpsTask(message.taskName(), () -> persistDecodedLocation(message));
    }

    public void evictPetCache(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }
        petByTerminalIdCache.invalidate(terminalId);
        log.info("Evicted GPS pet cache terminalId={}", terminalId);
    }

    private Optional<CachedPetLookup> findPetByTerminalIdCached(String terminalId) {
        CachedPetLookup cached = petByTerminalIdCache.getIfPresent(terminalId);
        if (cached != null) {
            if (!cached.found()) {
                log.debug("GPS pet cache hit unknown terminalId={}", terminalId);
                return Optional.empty();
            }
            log.debug("GPS pet cache hit terminalId={} petId={}", terminalId, cached.petId());
            return Optional.of(cached);
        }

        Optional<Pet> petOpt = petRepository.findByTerminalId(terminalId);
        if (petOpt.isEmpty()) {
            petByTerminalIdCache.put(terminalId, new CachedPetLookup(null, null, unknownTerminalIdCacheTtl));
            log.info("GPS pet cache miss terminalId={} result=unknown ttlSeconds={}",
                    terminalId, unknownTerminalIdCacheTtl.toSeconds());
            return Optional.empty();
        }

        Pet pet = petOpt.get();
        CachedPetLookup lookup = new CachedPetLookup(pet.getId(), pet.getName(), knownTerminalIdCacheTtl);
        petByTerminalIdCache.put(terminalId, lookup);
        log.info("GPS pet cache miss terminalId={} result=petId={} petName={} ttlSeconds={}",
                terminalId, pet.getId(), pet.getName(), knownTerminalIdCacheTtl.toSeconds());
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

    public boolean isMessageQueueEnabled() {
        return messageQueueEnabled;
    }

    public int getMessageQueueDepth() {
        return messageQueue.size();
    }

    public long getEnqueuedMessages() {
        return enqueuedMessages.get();
    }

    public long getDroppedMessages() {
        return droppedMessages.get();
    }

    public long getProcessedQueuedMessages() {
        return processedQueuedMessages.get();
    }

    private Location saveToDatabase(Location l) {
        return locationRepository.save(l);
    }

    private void enqueueMessage(GpsLocationMessage message) {
        if (!messageQueue.offer(message)) {
            droppedMessages.incrementAndGet();
            log.warn("GPS message queue full. Dropping decoded location task={}", message.taskName());
            return;
        }
        enqueuedMessages.incrementAndGet();
    }

    private void consumeQueuedMessages() {
        while (messageQueueRunning.get() || !messageQueue.isEmpty()) {
            try {
                GpsLocationMessage message = messageQueue.poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    continue;
                }
                persistDecodedLocation(message);
                processedQueuedMessages.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                log.warn("GPS queued message failed: {}", ex.getMessage(), ex);
            }
        }
    }

    private void persistDecodedLocation(GpsLocationMessage message) {
        Optional<CachedPetLookup> cachedPet = findPetByTerminalIdCached(message.terminalId());
        if (cachedPet.isEmpty()) {
            log.warn("GPS {} location for unknown terminalId={} remote={} lat={} lon={} context='{}'. Skipping persist.",
                    message.protocol(), message.terminalId(), message.remoteAddress(), message.latitude(), message.longitude(), message.context());
            return;
        }

        CachedPetLookup pet = cachedPet.get();
        LocalDateTime timestamp = appTimeService.fromInstant(message.instant());
        Location location = Location.builder()
                .pet(Pet.builder().id(pet.petId()).build())
                .timestamp(timestamp)
                .latitude(message.latitude())
                .longitude(message.longitude())
                .batteryPercent(message.batteryPercent())
                .batteryVoltage(message.batteryVoltage())
                .build();

        Location savedLocation = saveToDatabase(location);
        // Expensive success-path log: fires once per saved GPS point and duplicates location telemetry.
        // log.info("Persisted GPS {} location petId={} petName={} terminalId={} lat={} lon={} timestamp={} gpsValid={} batteryPercent={} batteryVoltage={} remote={}",
        //         message.protocol(), pet.petId(), pet.petName(), message.terminalId(), message.latitude(), message.longitude(), timestamp,
        //         message.gpsValid(), message.batteryPercent(), message.batteryVoltage(), message.remoteAddress());
        geofencingService.checkGeofence(savedLocation);
    }

    private record GpsLocationMessage(
            String protocol,
            String terminalId,
            double latitude,
            double longitude,
            Instant instant,
            boolean gpsValid,
            Integer batteryPercent,
            Double batteryVoltage,
            String remoteAddress,
            String context
    ) {
        private String taskName() {
            return protocol + "-" + terminalId;
        }
    }
}
