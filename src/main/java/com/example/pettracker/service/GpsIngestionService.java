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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GpsIngestionService {
    private static final int GEOFENCE_RETRY_CAPACITY = 1_000;
    private static final int GEOFENCE_RETRY_BATCH_SIZE = 100;

    private final LocationRepository locationRepository;
    private final PetRepository petRepository;
    private final GeofencingService geofencingService;
    private final BlockingQueue<Location> pendingGeofences = new ArrayBlockingQueue<>(GEOFENCE_RETRY_CAPACITY);
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
    private final int messageQueueCapacity;
    private final long directLocationCapacity;
    private final Object locationQueueLock = new Object();
    // A pet has one linked terminal. Use that identity before any asynchronous lookup can reorder arrivals.
    private final Map<String, Deque<QueuedGpsLocation>> locationsByTerminal = new HashMap<>();
    private final Deque<String> readyTerminals = new ArrayDeque<>();
    private boolean acceptingLocations = true;
    private int pendingLocations;
    private int runningLocations;
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
    private final ConcurrentHashMap<Long, PetPersistenceLock> petPersistenceLocks = new ConcurrentHashMap<>();

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
            @Value("${gps.ingestion.message-queue-shutdown-timeout-seconds:30}") int messageQueueShutdownTimeoutSeconds,
            @Value("${gps.ingestion.queue-capacity:500}") int directQueueCapacity,
            @Value("${gps.ingestion.max-threads:4}") int directMaxThreads) {
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
        this.messageQueueCapacity = Math.max(1, messageQueueCapacity);
        this.directLocationCapacity = (long) Math.max(0, directQueueCapacity) + Math.max(1, directMaxThreads);
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
                messageQueueConsumers, 30, 500, 4);
    }

    @PostConstruct
    void startMessageQueue() {
        warmTerminalCache();
        if (!messageQueueEnabled) {
            log.info("GPS ingestion message queue is disabled; decoded locations use direct async persistence.");
            return;
        }

        for (int i = 0; i < messageQueueConsumers; i++) {
            Thread worker = new Thread(this::consumeQueuedMessages, "GpsMessageQueue-" + (i + 1));
            worker.setDaemon(true);
            worker.start();
            messageQueueWorkers.add(worker);
        }
        log.info("GPS ingestion message queue enabled capacity={} consumers={}", messageQueueCapacity, messageQueueConsumers);
    }

    @PreDestroy
    void stopMessageQueue() {
        synchronized (locationQueueLock) {
            acceptingLocations = false;
            locationQueueLock.notifyAll();
        }
        if (!messageQueueEnabled) return;
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
        List<QueuedGpsLocation> abandoned = new ArrayList<>();
        synchronized (locationQueueLock) {
            locationsByTerminal.values().forEach(abandoned::addAll);
            locationsByTerminal.clear();
            readyTerminals.clear();
            pendingLocations = 0;
        }
        if (!abandoned.isEmpty()) {
            droppedMessages.addAndGet(abandoned.size());
            failedTasks.addAndGet(abandoned.size());
            abandoned.forEach(queued -> queued.completion().complete(GpsIngestionResult.FAILED));
            log.error("GPS ingestion shutdown; abandoned {} queued locations", abandoned.size());
        }
    }

    @Transactional
    public Location processGpsUpdate(Location l) {
        if (l.getTimestamp() != null && l.getTimestamp().isAfter(appTimeService.now())) {
            throw new IllegalArgumentException("Manual location timestamp cannot be in the future");
        }
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
            return CompletableFuture.completedFuture(GpsIngestionResult.UNKNOWN_TERMINAL);
        }
        Instant receivedAt = Instant.now();

        GpsLocationMessage message = new GpsLocationMessage(
                protocol,
                terminalId,
                latitude,
                longitude,
                instant == null ? receivedAt : instant,
                receivedAt,
                gpsValid,
                batteryPercent,
                batteryVoltage,
                remoteAddress,
                context
        );

        CompletableFuture<GpsIngestionResult> completion = new CompletableFuture<>();
        enqueueLocation(new QueuedGpsLocation(message, completion));
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

    private void enqueueLocation(QueuedGpsLocation queued) {
        String terminalId = queued.message().terminalId();
        boolean scheduleDirectly = false;
        synchronized (locationQueueLock) {
            boolean full = messageQueueEnabled ? pendingLocations >= messageQueueCapacity
                    : (long) pendingLocations + runningLocations >= directLocationCapacity;
            if (acceptingLocations && !full) {
                Deque<QueuedGpsLocation> terminalQueue = locationsByTerminal.get(terminalId);
                boolean first = terminalQueue == null;
                if (first) {
                    terminalQueue = new ArrayDeque<>();
                    locationsByTerminal.put(terminalId, terminalQueue);
                }
                terminalQueue.addLast(queued);
                pendingLocations++;
                if (messageQueueEnabled) {
                    enqueuedMessages.incrementAndGet();
                    if (first) {
                        readyTerminals.addLast(terminalId);
                        locationQueueLock.notify();
                    }
                } else if (first) {
                    scheduleDirectly = true;
                }
                if (!scheduleDirectly) return;
            }
        }
        if (scheduleDirectly) {
            scheduleTerminal(terminalId);
        } else {
            (messageQueueEnabled ? droppedMessages : droppedTasks).incrementAndGet();
            failedTasks.incrementAndGet();
            queued.completion().complete(GpsIngestionResult.FAILED);
            log.warn("GPS ingestion full or shutting down; dropping task={}", queued.message().taskName());
        }
    }

    private void scheduleTerminal(String terminalId) {
        try {
            gpsExecutor.execute(() -> drainTerminal(terminalId));
        } catch (RejectedExecutionException ex) {
            Deque<QueuedGpsLocation> rejected;
            synchronized (locationQueueLock) {
                rejected = locationsByTerminal.remove(terminalId);
                pendingLocations -= rejected.size();
            }
            droppedTasks.addAndGet(rejected.size());
            failedTasks.addAndGet(rejected.size());
            rejected.forEach(queued -> queued.completion().complete(GpsIngestionResult.FAILED));
            log.warn("GPS executor rejected {} locations for terminalId={}", rejected.size(), terminalId);
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
        synchronized (locationQueueLock) {
            return messageQueueEnabled ? pendingLocations : 0;
        }
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

    private void consumeQueuedMessages() {
        try {
            while (true) {
                String terminalId;
                synchronized (locationQueueLock) {
                    while (readyTerminals.isEmpty() && acceptingLocations) {
                        locationQueueLock.wait();
                    }
                    terminalId = readyTerminals.pollFirst();
                    if (terminalId == null) return;
                }
                drainTerminal(terminalId);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void drainTerminal(String terminalId) {
        while (true) {
            QueuedGpsLocation queued;
            synchronized (locationQueueLock) {
                Deque<QueuedGpsLocation> terminalQueue = locationsByTerminal.get(terminalId);
                if (terminalQueue == null) return;
                queued = terminalQueue.pollFirst();
                if (queued == null) {
                    locationsByTerminal.remove(terminalId);
                    return;
                }
                pendingLocations--;
                runningLocations++;
            }
            try {
                persistAndComplete(queued);
            } catch (RuntimeException ex) {
                failedTasks.incrementAndGet();
                queued.completion().complete(GpsIngestionResult.FAILED);
                log.warn("GPS location failed task={}", queued.message().taskName(), ex);
            } finally {
                synchronized (locationQueueLock) {
                    runningLocations--;
                }
                if (messageQueueEnabled) processedQueuedMessages.incrementAndGet();
            }
        }
    }

    private void persistAndComplete(QueuedGpsLocation queued) {
        GpsIngestionResult result = persistDecodedLocation(queued.message());
        if (result == GpsIngestionResult.PERSISTED) persistedLocations.incrementAndGet();
        else if (result == GpsIngestionResult.REJECTED || result == GpsIngestionResult.UNKNOWN_TERMINAL) {
            rejectedLocations.incrementAndGet();
        }
        queued.completion().complete(result);
    }

    private GpsIngestionResult persistDecodedLocation(GpsLocationMessage message) {
        Optional<CachedPetLookup> cachedPet = findPetByTerminalIdCached(message.terminalId());
        if (cachedPet.isEmpty()) {
            log.warn("GPS {} location for unknown terminalId={} remote={} lat={} lon={} context='{}'. Skipping persist.",
                    message.protocol(), message.terminalId(), message.remoteAddress(), message.latitude(), message.longitude(), message.context());
            return GpsIngestionResult.UNKNOWN_TERMINAL;
        }
        if (!isValidGpsFix(message)) return GpsIngestionResult.REJECTED;

        CachedPetLookup pet = cachedPet.get();
        LocalDateTime timestamp = appTimeService.fromInstant(message.instant());
        Location location = Location.builder()
                .pet(Pet.builder().id(pet.petId()).build())
                .timestamp(timestamp)
                .trackerRecorded(true)
                .latitude(message.latitude())
                .longitude(message.longitude())
                .batteryPercent(message.batteryPercent())
                .batteryVoltage(message.batteryVoltage())
                .build();

        // Different terminal IDs can briefly map to one pet while its tracker is being changed.
        PetPersistenceLock lock = petPersistenceLocks.compute(pet.petId(), (id, existing) -> {
            PetPersistenceLock current = existing == null ? new PetPersistenceLock() : existing;
            current.users++;
            return current;
        });
        try {
            synchronized (lock) {
                LocalDateTime latestTimestamp = locationRepository.findLatestTrackerTimestamp(pet.petId()).orElse(null);
                if (latestTimestamp != null && !timestamp.isAfter(latestTimestamp)) {
                    log.warn("Rejecting duplicate/out-of-order GPS fix protocol={} terminalId={} timestamp={} latest={}",
                            message.protocol(), message.terminalId(), timestamp, latestTimestamp);
                    return GpsIngestionResult.REJECTED;
                }
                Location savedLocation = saveToDatabase(location);
                submitTrackerGeofence(savedLocation);
            }
            return GpsIngestionResult.PERSISTED;
        } finally {
            petPersistenceLocks.compute(pet.petId(), (id, current) -> --current.users == 0 ? null : current);
        }
    }

    private void submitTrackerGeofence(Location location) {
        try {
            geofencingService.checkGeofence(location);
        } catch (RejectedExecutionException ex) {
            if (pendingGeofences.offer(location)) {
                log.debug("Geofence executor saturated; queued retry for locationId={}", location.getId());
            } else {
                log.error("Geofence retry queue full; unable to queue locationId={}", location.getId());
            }
        }
    }

    @Scheduled(fixedDelayString = "${gps.geofence.retry-delay-ms:1000}")
    void retryPendingGeofences() {
        for (int i = 0; i < GEOFENCE_RETRY_BATCH_SIZE; i++) {
            Location location = pendingGeofences.peek();
            if (location == null) return;
            try {
                geofencingService.checkGeofence(location);
            } catch (RejectedExecutionException ex) {
                // Leave the work queued for the next scheduled run if the executor is still full.
                return;
            }
            pendingGeofences.remove();
        }
    }

    private boolean isValidGpsFix(GpsLocationMessage message) {
        double latitude = message.latitude();
        double longitude = message.longitude();
        if (!message.gpsValid() || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            log.warn("Rejecting invalid GPS fix protocol={} terminalId={} valid={} latitude={} longitude={} remote={}",
                    message.protocol(), message.terminalId(), message.gpsValid(), latitude, longitude, message.remoteAddress());
            return false;
        }
        Instant receivedAt = message.receivedAt();
        Instant positionTime = message.instant();
        if (positionTime.isBefore(receivedAt.minus(maximumPositionAge))
                || positionTime.isAfter(receivedAt.plus(maximumFuturePositionSkew))) {
            log.warn("Rejecting stale/future GPS fix protocol={} terminalId={} positionTime={} receivedAt={} remote={}",
                    message.protocol(), message.terminalId(), positionTime, receivedAt, message.remoteAddress());
            return false;
        }
        return true;
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

    private static final class PetPersistenceLock {
        private int users;
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
            Instant receivedAt,
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
