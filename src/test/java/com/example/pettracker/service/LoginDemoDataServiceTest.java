package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginDemoDataServiceTest {
    private final PetRepository pets = mock(PetRepository.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final AppTimeService time = mock(AppTimeService.class);
    private final DemoAccount account = new DemoAccount("danico411@gmail.com", true);
    private final User owner = User.builder().id(42L).email("danico411@gmail.com").build();
    private final Map<String, Pet> storedPets = new HashMap<>();
    private final Map<Long, Location> latestLocations = new HashMap<>();
    private final List<Location> storedLocations = new ArrayList<>();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0);
    private LoginDemoDataService service;

    @BeforeEach
    void setUp() {
        when(time.now()).thenReturn(now);
        when(pets.findByImei(anyString())).thenAnswer(call -> Optional.ofNullable(storedPets.get(call.getArgument(0))));
        when(pets.save(any(Pet.class))).thenAnswer(call -> {
            Pet pet = call.getArgument(0);
            if (pet.getId() == null) pet.setId(100L + storedPets.size());
            storedPets.put(pet.getImei(), pet);
            return pet;
        });
        when(locations.findTopByPetIdOrderByTimestampDesc(anyLong()))
                .thenAnswer(call -> Optional.ofNullable(latestLocations.get(call.getArgument(0))));
        when(locations.save(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            storedLocations.add(location);
            latestLocations.put(location.getPet().getId(), location);
            return location;
        });
        service = new LoginDemoDataService(pets, locations, account, time);
    }

    @Test
    void ordinaryAccountsNeverGetDemoData() {
        service.seedForLogin(User.builder().id(43L).email("another@example.com").build());

        verifyNoInteractions(pets, locations);
    }

    @Test
    void disabledDemoDoesNotSeedEvenForTheDemoAccount() {
        new LoginDemoDataService(pets, locations, new DemoAccount(owner.getEmail(), false), time)
                .seedForLogin(owner);

        verifyNoInteractions(pets, locations);
    }

    @Test
    void seedsOnlyTheDemoAccountsSyntheticPetsAndUsesAppTime() {
        service.seedForLogin(owner);

        assertThat(storedPets).containsOnlyKeys("DEMO-PELUCHE-42", "DEMO-BELLA-42", "DEMO-ROCKY-42");
        assertThat(storedPets.values()).allSatisfy(pet -> {
            assertThat(pet.getOwner()).isSameAs(owner);
            assertThat(pet.getTerminalId()).isNull();
            assertThat(pet.getCreatedAt()).isNotNull();
        });
        assertThat(storedLocations).hasSize(34).allSatisfy(location -> {
            assertThat(location.getPet().getOwner().getId()).isEqualTo(42L);
            assertThat(location.getTimestamp()).isBetween(now.minusHours(3), now);
        });
        verify(pets, never()).findByOwnerIdAndNameIgnoreCase(anyLong(), anyString());
    }

    @Test
    void repeatedLoginsDoNotDuplicatePetsOrRecentRoutes() {
        service.seedForLogin(owner);
        service.seedForLogin(owner);

        verify(pets, times(3)).save(any());
        assertThat(storedLocations).hasSize(34);
    }

    @Test
    void refreshesOldRoutesWithoutCreatingMorePets() {
        service.seedForLogin(owner);
        when(time.now()).thenReturn(now.plusHours(4));
        service.seedForLogin(owner);

        verify(pets, times(3)).save(any());
        assertThat(storedLocations).hasSize(68);
        assertThat(latestLocations.values()).allSatisfy(location ->
                assertThat(location.getTimestamp()).isEqualTo(now.plusHours(4)));
    }

    @Test
    void doesNotOverwriteARealPetWithTheSameName() {
        Pet realBella = Pet.builder().id(9L).owner(owner).name("Bella").imei("123456789012345")
                .terminalId("014166272782").build();
        storedPets.put(realBella.getImei(), realBella);

        service.seedForLogin(owner);

        assertThat(realBella.getImei()).isEqualTo("123456789012345");
        assertThat(realBella.getTerminalId()).isEqualTo("014166272782");
        verify(pets, never()).save(realBella);
        assertThat(storedLocations).noneMatch(location -> location.getPet().getId().equals(9L));
    }

    @Test
    void skipsSyntheticIdsOwnedByAnotherAccountAndPetsLinkedToRealTrackers() {
        User other = User.builder().id(43L).email("other@example.com").build();
        Pet otherBella = Pet.builder().id(8L).name("Bella").owner(other).imei("DEMO-BELLA-42").build();
        Pet trackedPeluche = Pet.builder().id(9L).name("Peluche").owner(owner).imei("DEMO-PELUCHE-42")
                .terminalId("014166272782").build();
        storedPets.put(otherBella.getImei(), otherBella);
        storedPets.put(trackedPeluche.getImei(), trackedPeluche);

        service.seedForLogin(owner);

        verify(pets, never()).save(otherBella);
        verify(pets, never()).save(trackedPeluche);
        assertThat(storedLocations).hasSize(7).allSatisfy(location ->
                assertThat(location.getPet().getImei()).isEqualTo("DEMO-ROCKY-42"));
    }
}
