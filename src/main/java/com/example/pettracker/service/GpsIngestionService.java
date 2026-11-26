package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
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
    private final Executor gpsTerminalLookupExecutor;
    private final AppTimeService appTimeService;
    private final Cache<String, CachedPetLookup> petByTerminalIdCache;
    private final Duration knownTerminalIdCacheTtl;
    private final Duration unknownTerminalIdCacheTtl;
    private final Duration maximumPositionAge;
    private final Duration maximumFuturePositionSkew;
    private final boolean messageQueueEnabled;
    private final int messageQueueConsumers;
    private final int messageQueueShutdownTimeoutSeconds;
    private final BlockingQueue<QueuedGpsLocation> messageQueue;
    private final AtomicBoolean messageQueueRunning = new AtomicBoolean();
    private final List<Thread> messageQueueWorkers = new ArrayList<>();
    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicLong receivedLocations = new AtomicLong();
    private final AtomicLong enqueuedMessages = new AtomicLong();
    private final AtomicLong droppedMessages = new AtomicLong();
    private final AtomicLong processedQueuedMessages = new AtomicLong();
    private final AtomicLong failedTasks = new AtomicLong();
    private final AtomicLong persistedLocations = new AtomicLong();
    private final AtomicLong rejectedLocations = new AtomicLong();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> terminalLookupsInFlight = new ConcurrentHashMap<>();
    private final Object[] terminalCacheLocks = createTerminalCacheLocks();
    private final Object[] petPersistenceLocks = createTerminalCacheLocks();

    @Autowired
    public GpsIngestionService(
            LocationRepository locationRepository,
            PetRepository petRepository,
            GeofencingService geofencingService,
            @Qualifier("gpsExecutor") Executor gpsExecutor,
            @Qualifier("gpsTerminalLookupExecutor") Executor gpsTerminalLookupExecutor,
            AppTimeService appTimeService,
            @Qualifier("gpsPetByTerminalIdCache") Cache<String, CachedPetLookup> petByTerminalIdCache,
            @Value("${gps.cache.pet-by-terminal-id.known-ttl:PT24H}") Duration knownTerminalIdCacheTtl,
            @Value("${gps.cache.pet-by-terminal-id.unknown-ttl:PT1M}") Duration unknownTerminalIdCacheTtl,
            @Value("${gps.ingestion.maximum-position-age:PT10M}") Duration maximumPositionAge,
            @Value("${gps.ingestion.maximum-future-position-skew:PT5M}") Duration maximumFuturePositionSkew,
            @Value("${gps.ingestion.message-queue.enabled:false}") boolean messageQueueEnabled,
            @Value("${gps.ingestion.message-queue.capacity:10000}") int messageQueueCapacity,
            @Value("${gps.ingestion.message-queue.consumers:1}") int messageQueueConsumers,
            @Value("${gps.ingestion.message-queue-shutdown-timeout-seconds:30}") int messageQueueShutdownTimeoutSeconds) {
        this.locationRepository = locationRepository;
        this.petRepository = petRepository;
        this.geofencingService = geofencingService;
        this.gpsExecutor = gpsExecutor;
        this.gpsTerminalLookupExecutor = gpsTerminalLookupExecutor;
        this.appTimeService = appTimeService;
        this.petByTerminalIdCache = petByTerminalIdCache;
        this.knownTerminalIdCacheTtl = knownTerminalIdCacheTtl;
        this.unknownTerminalIdCacheTtl = unknownTerminalIdCacheTtl;
        this.maximumPositionAge = maximumPositionAge;
        this.maximumFuturePositionSkew = maximumFuturePositionSkew;
        this.messageQueueEnabled = messageQueueEnabled;
        this.messageQueueConsumers = Math.max(1, messageQueueConsumers);
        this.messageQueueShutdownTimeoutSeconds = Math.max(1, messageQueueShutdownTimeoutSeconds);
        this.messageQueue = new LinkedBlockingQueue<>(Math.max(1, messageQueueCapacity));
    }

    /** Kept for direct construction in existing protocol adapters; Spring uses the bounded lookup executor above. */
    public GpsIngestionService(LocationRepository locationRepository, PetRepository petRepository,
                               GeofencingService geofencingService, Executor gpsExecutor,
                               AppTimeService appTimeService, Cache<String, CachedPetLookup> petByTerminalIdCache,
                               Duration knownTerminalIdCacheTtl, Duration unknownTerminalIdCacheTtl,
                               boolean messageQueueEnabled, int messageQueueCapacity, int messageQueueConsumers) {
        this(locationRepository, petRepository, geofencingService, gpsExecutor, Runnable::run, appTimeService,
                petByTerminalIdCache, knownTerminalIdCacheTtl, unknownTerminalIdCacheTtl,
                Duration.ofMinutes(10), Duration.ofMinutes(5), messageQueueEnabled, messageQueueCapacity,
                messageQueueConsumers, 30);
    }

    @PostConstruct
    void startMessageQueue() {
        warmTerminalCache();
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
        if (!messageQueueEnabled || messageQueueWorkers.isEmpty()) return;
        messageQueueRunning.set(false);
        messageQueueWorkers.forEach(Thread::interrupt);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(messageQueueShutdownTimeoutSeconds);
        for (Thread worker : messageQueueWorkers) {
            try {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) break;
                worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!messageQueue.isEmpty()) {
            int abandoned = 0;
            QueuedGpsLocation queued;
            while ((queued = messageQueue.poll()) != null) {
                abandoned++;
                queued.completion().complete(GpsIngestionResult.FAILED);
            }
            droppedMessages.addAndGet(abandoned);
            failedTasks.addAndGet(abandoned);
            log.error("GPS ingestion shutdown timed out; abandoned {} queued locations", abandoned);
        }
    }

    @Transactional
    public Location processGpsUpdate(Location l) {
        Location savedLocation = saveToDatabase(l);
        geofencingService.checkGeofence(savedLocation);
        return savedLocation;
    }

    public CompletableFuture<GpsIngestionResult> processDecodedLocation(
            String protocol,
            String terminalId,
            double latitude,
            double longitude,
            Instant instant,
            boolean gpsValid,
            String remoteAddress,
            String context
    ) {
        return processDecodedLocation(protocol, terminalId, latitude, longitude, instant, gpsValid, null, null, remoteAddress, context);
    }

    public CompletableFuture<GpsIngestionResult> processDecodedLocation(
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
        receivedLocations.incrementAndGet();
        if (terminalId == null || terminalId.isBlank()) {
            log.warn("GPS {} location missing terminalId before async enqueue remote={} context='{}'", protocol, remoteAddress, context);
            rejectedLocations.incrementAndGet();
            return CompletableFuture.completedFuture(GpsIngestionResult.REJECTED);
        }
        if (!gpsValid || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            rejectedLocations.incrementAndGet();
            log.warn("Rejecting invalid GPS fix protocol={} terminalId={} valid={} latitude={} longitude={} remote={}",
                    protocol, terminalId, gpsValid, latitude, longitude, remoteAddress);
            return CompletableFuture.completedFuture(GpsIngestionResult.REJECTED);
        }
        Instant receivedAt = Instant.now();
        Instant positionTime = instant == null ? receivedAt : instant;
        if (positionTime.isBefore(receivedAt.minus(maximumPositionAge))
                || positionTime.isAfter(receivedAt.plus(maximumFuturePositionSkew))) {
            rejectedLocations.incrementAndGet();
            log.warn("Rejecting stale/future GPS fix protocol={} terminalId={} positionTime={} receivedAt={} remote={}",
                    protocol, terminalId, positionTime, receivedAt, remoteAddress);
            return CompletableFuture.completedFuture(GpsIngestionResult.REJECTED);
        }

        GpsLocationMessage message = new GpsLocationMessage(
                protocol,
                terminalId,
                latitude,
                longitude,
                positionTime,
                gpsValid,
                batteryPercent,
                batteryVoltage,
                remoteAddress,
                context
        );

        CompletableFuture<GpsIngestionResult> completion = new CompletableFuture<>();
        QueuedGpsLocation queued = new QueuedGpsLocation(message, completion);
        if (messageQueueEnabled) {
            enqueueMessage(queued);
            return completion;
        }

        submitGpsTask(message.taskName(), queued);
        return completion;
    }

    public boolean isKnownTerminalId(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) return false;
        CachedPetLookup cached = petByTerminalIdCache.getIfPresent(terminalId);
        if (cached != null) return cached.found();
        refreshTerminalCacheAsync(terminalId);
        return false;
    }

    public CompletableFuture<Boolean> isKnownTerminalIdAsync(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) return CompletableFuture.completedFuture(false);
        CachedPetLookup cached = petByTerminalIdCache.getIfPresent(terminalId);
        if (cached != null) return CompletableFuture.completedFuture(cached.found());

        CompletableFuture<Boolean> pending = terminalLookupsInFlight.get(terminalId);
        if (pending != null) return pending;
        CompletableFuture<Boolean> lookup = new CompletableFuture<>();
        pending = terminalLookupsInFlight.putIfAbsent(terminalId, lookup);
        if (pending != null) return pending;
        try {
            gpsTerminalLookupExecutor.execute(() -> {
                try {
                    CachedPetLookup loaded = loadTerminalCacheEntry(terminalId);
                    lookup.complete(loaded.found());
                } catch (RuntimeException ex) {
                    log.warn("GPS terminal lookup failed terminalId={}", terminalId, ex);
                    lookup.complete(false);
                } finally {
                    terminalLookupsInFlight.remove(terminalId, lookup);
                }
            });
        } catch (RejectedExecutionException ex) {
            terminalLookupsInFlight.remove(terminalId, lookup);
            lookup.complete(false);
            log.warn("GPS terminal lookup executor rejected terminalId={}", terminalId, ex);
        }
        return lookup;
    }

    public void evictPetCache(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }
        petByTerminalIdCache.invalidate(terminalId);
        log.info("Evicted GPS pet cache terminalId={}", terminalId);
        Runnable refresh = () -> refreshTerminalCacheAsync(terminalId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    synchronized (terminalCacheLock(terminalId)) {
                        petByTerminalIdCache.invalidate(terminalId);
                    }
                    refresh.run();
                }
            });
        } else {
            refresh.run();
        }
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

        cached = loadTerminalCacheEntry(terminalId);
        if (!cached.found()) return Optional.empty();
        log.info("GPS pet cache miss terminalId={} result=petId={} ttlSeconds={}",
                terminalId, cached.petId(), knownTerminalIdCacheTtl.toSeconds());
        return Optional.of(cached);
    }

    private void submitGpsTask(String taskName, QueuedGpsLocation queued) {
        try {
            gpsExecutor.execute(() -> {
                try {
                    persistAndComplete(queued);
                } catch (RuntimeException ex) {
                    failedTasks.incrementAndGet();
                    queued.completion().complete(GpsIngestionResult.FAILED);
                    log.warn("GPS async task failed task={}: {}", taskName, ex.getMessage(), ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            droppedTasks.incrementAndGet();
            failedTasks.incrementAndGet();
            queued.completion().complete(GpsIngestionResult.FAILED);
            log.warn("GPS async queue full or executor is shutting down; dropping task={}", taskName, ex);
        }
    }

    public long getDroppedTasks() {
        return droppedTasks.get();
    }

    public long getReceivedLocations() { return receivedLocations.get(); }

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

    public long getFailedTasks() { return failedTasks.get(); }
    public long getPersistedLocations() { return persistedLocations.get(); }
    public long getRejectedLocations() { return rejectedLocations.get(); }

    private Location saveToDatabase(Location l) {
        return locationRepository.save(l);
    }

    private void enqueueMessage(QueuedGpsLocation queued) {
        if (!messageQueue.offer(queued)) {
            droppedMessages.incrementAndGet();
            failedTasks.incrementAndGet();
            queued.completion().complete(GpsIngestionResult.FAILED);
            log.warn("GPS message queue full. Dropping decoded location task={}", queued.message().taskName());
            return;
        }
        enqueuedMessages.incrementAndGet();
    }

    private void consumeQueuedMessages() {
        while (messageQueueRunning.get() || !messageQueue.isEmpty()) {
            try {
                QueuedGpsLocation queued = messageQueue.poll(1, TimeUnit.SECONDS);
                if (queued == null) {
                    continue;
                }
                try {
                    persistAndComplete(queued);
                } catch (RuntimeException ex) {
                    failedTasks.incrementAndGet();
                    queued.completion().complete(GpsIngestionResult.FAILED);
                    log.warn("GPS queued message failed: {}", ex.getMessage(), ex);
                } finally {
                    processedQueuedMessages.incrementAndGet();
                }
            } catch (InterruptedException ex) {
                if (messageQueueRunning.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void persistAndComplete(QueuedGpsLocation queued) {
        GpsIngestionResult result = persistDecodedLocation(queued.message());
        if (result == GpsIngestionResult.PERSISTED) persistedLocations.incrementAndGet();
        else if (result == GpsIngestionResult.REJECTED) rejectedLocations.incrementAndGet();
        queued.completion().complete(result);
    }

    private GpsIngestionResult persistDecodedLocation(GpsLocationMessage message) {
        Optional<CachedPetLookup> cachedPet = findPetByTerminalIdCached(message.terminalId());
        if (cachedPet.isEmpty()) {
            log.warn("GPS {} location for unknown terminalId={} remote={} lat={} lon={} context='{}'. Skipping persist.",
                    message.protocol(), message.terminalId(), message.remoteAddress(), message.latitude(), message.longitude(), message.context());
            return GpsIngestionResult.REJECTED;
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

        Object lock = petPersistenceLock(pet.petId());
        synchronized (lock) {
            Optional<Location> latest = locationRepository.findTopByPetIdOrderByTimestampDesc(pet.petId());
            LocalDateTime latestTimestamp = latest.map(Location::getTimestamp).orElse(null);
            if (latestTimestamp != null && !timestamp.isAfter(latestTimestamp)) {
                log.warn("Rejecting duplicate/out-of-order GPS fix protocol={} terminalId={} timestamp={} latest={}",
                        message.protocol(), message.terminalId(), timestamp, latestTimestamp);
                return GpsIngestionResult.REJECTED;
            }
            Location savedLocation = saveToDatabase(location);
            geofencingService.checkGeofence(savedLocation);
        }
        // Expensive success-path log: fires once per saved GPS point and duplicates location telemetry.
        // log.info("Persisted GPS {} location petId={} petName={} terminalId={} lat={} lon={} timestamp={} gpsValid={} batteryPercent={} batteryVoltage={} remote={}",
        //         message.protocol(), pet.petId(), pet.petName(), message.terminalId(), message.latitude(), message.longitude(), timestamp,
        //         message.gpsValid(), message.batteryPercent(), message.batteryVoltage(), message.remoteAddress());
        return GpsIngestionResult.PERSISTED;
    }

    private void warmTerminalCache() {
        try {
            List<PetRepository.TerminalPetLookup> pets = petRepository.findAllTerminalLookups();
            pets.forEach(pet -> petByTerminalIdCache.put(pet.getTerminalId(), new CachedPetLookup(
                    pet.getId(), pet.getName(), knownTerminalIdCacheTtl)));
            log.info("Warmed GPS terminal cache entries={} cached={} maximumSize={}",
                    pets.size(), petByTerminalIdCache.estimatedSize(), petByTerminalIdCache.policy().eviction()
                            .map(policy -> policy.getMaximum()).orElse(-1L));
        } catch (RuntimeException ex) {
            log.error("Failed to warm GPS terminal cache; cold terminal lookups will be rejected until refreshed", ex);
        }
    }

    private void refreshTerminalCacheAsync(String terminalId) {
        isKnownTerminalIdAsync(terminalId).whenComplete((known, error) -> {
            if (error != null) log.warn("GPS terminal cache refresh failed terminalId={}", terminalId, error);
        });
    }

    private CachedPetLookup loadTerminalCacheEntry(String terminalId) {
        synchronized (terminalCacheLock(terminalId)) {
            Optional<Pet> pet = petRepository.findByTerminalId(terminalId);
            CachedPetLookup entry = pet.map(value -> new CachedPetLookup(value.getId(), value.getName(), knownTerminalIdCacheTtl))
                    .orElseGet(() -> new CachedPetLookup(null, null, unknownTerminalIdCacheTtl));
            petByTerminalIdCache.put(terminalId, entry);
            return entry;
        }
    }

    private Object terminalCacheLock(String terminalId) {
        return terminalCacheLocks[Math.floorMod(terminalId.hashCode(), terminalCacheLocks.length)];
    }

    private Object petPersistenceLock(Long petId) {
        return petPersistenceLocks[Math.floorMod(Long.hashCode(petId), petPersistenceLocks.length)];
    }

    private static Object[] createTerminalCacheLocks() {
        Object[] locks = new Object[256];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
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

    private record QueuedGpsLocation(GpsLocationMessage message, CompletableFuture<GpsIngestionResult> completion) { }
}
