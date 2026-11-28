package com.example.pettracker.service;

import com.example.pettracker.controller.ApiExceptionHandler;
import com.example.pettracker.controller.PetController;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationTimestampTest {
    private static final long PET_ID = 1L;
    private static final String TERMINAL = "100000000000001";

    private final AppTimeService clock = new AppTimeService("UTC");
    private final LocationRepository locations = mock(LocationRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final GpsIngestionService ingestion = new GpsIngestionService(
            locations, pets, mock(GeofencingService.class), Runnable::run, clock,
            Caffeine.newBuilder().build(), Duration.ofDays(1), Duration.ofMinutes(1), false, 10, 1);

    @Test
    void postingAFutureManualLocationReturnsBadRequestWithoutSaving() throws Exception {
        User owner = User.builder().id(2L).role(User.Role.USER).build();
        Pet pet = Pet.builder().id(PET_ID).owner(owner).build();
        var authentication = new UsernamePasswordAuthenticationToken("owner", null, List.of());
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.require(authentication)).thenReturn(owner);
        when(pets.findById(PET_ID)).thenReturn(Optional.of(pet));

        LocationService locationService = new LocationService(locations, ingestion, null, null, clock);
        ImageUploadLimits imageLimits = mock(ImageUploadLimits.class);
        PetService petService = new PetService(pets, ingestion, currentUser, null, locationService,
                null, null, imageLimits);
        var mvc = MockMvcBuilders.standaloneSetup(new PetController(petService))
                .setControllerAdvice(new ApiExceptionHandler(imageLimits))
                .build();

        mvc.perform(post("/api/pets/{id}/locations", PET_ID)
                        .principal(authentication)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":4.71,"longitude":-74.07,"timestamp":"%s"}
                                """.formatted(Instant.now().plus(Duration.ofDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Manual location timestamp cannot be in the future"));
        verify(locations, never()).save(any(Location.class));
    }

    @Test
    void savedFutureManualLocationDoesNotBlockTrackerOrDisableDuplicateDetection() {
        Pet pet = Pet.builder().id(PET_ID).name("Luna").terminalId(TERMINAL).build();
        when(pets.findByTerminalId(TERMINAL)).thenReturn(Optional.of(pet));

        Instant trackerTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5);
        Location futureManual = Location.builder().pet(pet)
                .timestamp(clock.fromInstant(trackerTime.plus(Duration.ofDays(1)))).build();
        List<Location> saved = new ArrayList<>(List.of(futureManual));
        when(locations.findTopByPetIdOrderByTimestampDesc(PET_ID)).thenReturn(Optional.of(futureManual));
        when(locations.findLatestTrackerTimestamp(PET_ID)).thenAnswer(call -> saved.stream()
                .filter(Location::isTrackerRecorded)
                .map(Location::getTimestamp)
                .max(Comparator.naturalOrder()));
        when(locations.save(any(Location.class))).thenAnswer(call -> {
            Location point = call.getArgument(0);
            saved.add(point);
            return point;
        });

        assertThat(submitTrackerPoint(trackerTime)).isEqualTo(GpsIngestionResult.PERSISTED);
        assertThat(submitTrackerPoint(trackerTime)).isEqualTo(GpsIngestionResult.REJECTED);
        assertThat(saved).hasSize(2);
        assertThat(saved.getFirst()).isSameAs(futureManual);
        assertThat(saved.getLast().isTrackerRecorded()).isTrue();
        assertThat(saved.getLast().getTimestamp()).isEqualTo(clock.fromInstant(trackerTime));
    }

    private GpsIngestionResult submitTrackerPoint(Instant timestamp) {
        return ingestion.processDecodedLocation("V41", TERMINAL, 4.71, -74.07, timestamp, true,
                "test", "location timestamp").join();
    }
}
