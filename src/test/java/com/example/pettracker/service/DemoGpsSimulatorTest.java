package com.example.pettracker.service;

import com.example.pettracker.entity.*;
import com.example.pettracker.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DemoGpsSimulatorTest {
    private final PetRepository pets = mock(PetRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final GeofenceRepository geofences = mock(GeofenceRepository.class);
    private final AlertService alerts = mock(AlertService.class);
    private final AppTimeService time = mock(AppTimeService.class);
    private final DemoAccount account = new DemoAccount("danico411@gmail.com", true);
    private final User owner = User.builder().id(42L).email("danico411@gmail.com").build();
    private final Map<String, Pet> storedPets = new HashMap<>();
    private final List<Location> published = new ArrayList<>();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0);
    private DemoGpsSimulator simulator;

    @BeforeEach
    void setUp() {
        when(time.now()).thenReturn(now);
        when(users.findByEmail(account.email())).thenReturn(Optional.of(owner));
        when(pets.findByImei(anyString())).thenAnswer(call -> Optional.ofNullable(storedPets.get(call.getArgument(0))));
        for (String name : List.of("Bella", "Peluche", "Rocky")) {
            Pet pet = Pet.builder().id(100L + storedPets.size()).name(name).owner(owner)
                    .imei(account.imeiFor(owner, name)).build();
            storedPets.put(pet.getImei(), pet);
        }
        when(locations.save(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            published.add(location);
            return location;
        });
        when(geofences.save(any(Geofence.class))).thenAnswer(call -> call.getArgument(0));
        simulator = new DemoGpsSimulator(pets, geofences, users, locations, alerts, account, time);
    }

    @Test
    void publishesOnlySyntheticPetsFromTheDemoAccountAndAdvancesTheirRoutes() {
        simulator.publishNormalDemoLocations();
        simulator.publishNormalDemoLocations();

        assertThat(published).hasSize(6).allSatisfy(location -> {
            assertThat(location.getPet().getOwner().getId()).isEqualTo(42L);
            assertThat(location.getPet().getImei()).startsWith("DEMO-");
            assertThat(location.getTimestamp()).isEqualTo(now);
        });
        assertThat(published.get(3).getLatitude()).isGreaterThan(published.getFirst().getLatitude());
        verify(pets, never()).findAll();
        verifyNoInteractions(alerts);
    }

    @Test
    void missingDemoAccountProducesNoData() {
        when(users.findByEmail(account.email())).thenReturn(Optional.empty());

        simulator.publishNormalDemoLocations();
        simulator.triggerOutsideGeofenceEvent();

        verifyNoInteractions(pets, locations, geofences, alerts);
    }

    @Test
    void neverSimulatesAnotherOwnersPetEvenIfItsSyntheticIdMatches() {
        Pet bella = storedPets.get("DEMO-BELLA-42");
        bella.setOwner(User.builder().id(43L).email("other@example.com").build());

        simulator.publishNormalDemoLocations();
        simulator.triggerOutsideGeofenceEvent();

        assertThat(published).hasSize(2).noneMatch(location -> location.getPet() == bella);
        verifyNoInteractions(geofences, alerts);
    }

    @Test
    void neverSimulatesAPetThatHasARealTrackerTerminalId() {
        Pet bella = storedPets.get("DEMO-BELLA-42");
        bella.setTerminalId("014166272782");

        simulator.publishNormalDemoLocations();
        simulator.triggerOutsideGeofenceEvent();

        assertThat(published).hasSize(2).noneMatch(location -> location.getPet() == bella);
        verifyNoInteractions(geofences, alerts);
    }

    @Test
    void outsideEventStoresOnlyADemoLocationGeofenceAndInAppAlert() {
        simulator.triggerOutsideGeofenceEvent();

        Pet bella = storedPets.get("DEMO-BELLA-42");
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().getPet()).isSameAs(bella);
        verify(geofences).save(argThat(fence -> fence.getPet() == bella && fence.getRadiusMeters() == 500.0));
        verify(alerts).createAlert(eq(bella), eq(Alert.AlertType.BOUNDARY), startsWith("Demo geofence exit"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simulatorBeanHonorsTheEnableSwitch(boolean enabled) {
        new ApplicationContextRunner().withUserConfiguration(DemoGpsSimulator.class)
                .withPropertyValues("demo.gps.enabled=" + enabled)
                .withBean(PetRepository.class, () -> pets)
                .withBean(GeofenceRepository.class, () -> geofences)
                .withBean(UserRepository.class, () -> users)
                .withBean(LocationRepository.class, () -> locations)
                .withBean(AlertService.class, () -> alerts)
                .withBean(DemoAccount.class, () -> new DemoAccount(account.email(), enabled))
                .withBean(AppTimeService.class, () -> time)
                .run(context -> {
                    if (enabled) assertThat(context).hasSingleBean(DemoGpsSimulator.class);
                    else assertThat(context).doesNotHaveBean(DemoGpsSimulator.class);
                });
    }
}
