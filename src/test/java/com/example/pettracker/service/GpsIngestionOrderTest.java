package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class GpsIngestionOrderTest {
    private static final String LUNA_TERMINAL = "100000000000001";
    private static final String SOL_TERMINAL = "100000000000002";

    @ParameterizedTest(name = "FIFO per pet and parallel across pets (message queue enabled: {0})")
    @ValueSource(booleans = {false, true})
    void processesEachPetInArrivalOrderWithoutBlockingOtherPets(boolean messageQueueEnabled) throws Exception {
        Instant lunaFirstTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(10);
        CountDownLatch lunaFirstStarted = new CountDownLatch(1);
        CountDownLatch releaseLunaFirst = new CountDownLatch(1);
        AppTimeService clock = spy(new AppTimeService("UTC"));
        // Pause before the pet's persistence lock. A lock alone would allow Luna's second point to save first.
        doAnswer(call -> {
            lunaFirstStarted.countDown();
            if (!releaseLunaFirst.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release Luna's first point");
            }
            return call.callRealMethod();
        }).when(clock).fromInstant(lunaFirstTime);

        PetRepository pets = mock(PetRepository.class);
        when(pets.findByTerminalId(LUNA_TERMINAL)).thenReturn(Optional.of(Pet.builder().id(1L).name("Luna").build()));
        when(pets.findByTerminalId(SOL_TERMINAL)).thenReturn(Optional.of(Pet.builder().id(2L).name("Sol").build()));

        LocationRepository locations = mock(LocationRepository.class);
        Map<Long, Location> latestByPet = new ConcurrentHashMap<>();
        when(locations.findLatestTrackerTimestamp(anyLong()))
                .thenAnswer(call -> Optional.ofNullable(latestByPet.get(call.<Long>getArgument(0)))
                        .filter(Location::isTrackerRecorded)
                        .map(Location::getTimestamp));
        when(locations.save(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            latestByPet.put(location.getPet().getId(), location);
            return location;
        });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            GpsIngestionService ingestion = new GpsIngestionService(locations, pets, mock(GeofencingService.class),
                    executor, clock, Caffeine.newBuilder().build(), Duration.ofDays(1), Duration.ofMinutes(1),
                    messageQueueEnabled, 10, 2);
            ingestion.startMessageQueue();
            try {
                CompletableFuture<GpsIngestionResult> lunaFirst = submit(ingestion, LUNA_TERMINAL, lunaFirstTime);
                assertThat(lunaFirstStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Instant lunaSecondTime = lunaFirstTime.plusSeconds(1);
                CompletableFuture<GpsIngestionResult> lunaSecond = submit(ingestion, LUNA_TERMINAL, lunaSecondTime);
                CompletableFuture<GpsIngestionResult> sol = submit(ingestion, SOL_TERMINAL, lunaFirstTime.plusSeconds(2));

                assertThat(sol.get(5, TimeUnit.SECONDS)).isEqualTo(GpsIngestionResult.PERSISTED);
                assertThat(lunaFirst).isNotDone();
                assertThat(lunaSecond).isNotDone();
                assertThat(latestByPet).doesNotContainKey(1L).containsKey(2L);

                releaseLunaFirst.countDown();
                assertThat(lunaFirst.get(5, TimeUnit.SECONDS)).isEqualTo(GpsIngestionResult.PERSISTED);
                assertThat(lunaSecond.get(5, TimeUnit.SECONDS)).isEqualTo(GpsIngestionResult.PERSISTED);
                assertThat(latestByPet.get(1L).getTimestamp()).isEqualTo(clock.fromInstant(lunaSecondTime));
            } finally {
                releaseLunaFirst.countDown();
                ingestion.stopMessageQueue();
            }
        }
    }

    private static CompletableFuture<GpsIngestionResult> submit(
            GpsIngestionService ingestion, String terminalId, Instant timestamp) {
        return ingestion.processDecodedLocation("V41", terminalId, 4.71, -74.07, timestamp, true, "test", "arrival order");
    }
}
