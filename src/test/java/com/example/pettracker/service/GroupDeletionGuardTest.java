package com.example.pettracker.service;

import com.example.pettracker.entity.DogWalkerProfile;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.DogWalkerProfileRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GroupDeletionGuardTest {
    private final PetRepository pets = mock(PetRepository.class);
    private final SearchGroupRepository groups = mock(SearchGroupRepository.class);
    private final GpsIngestionService ingestion = mock(GpsIngestionService.class);
    private final PetService petService = new PetService(pets, ingestion, null, null, null, null, null,
            null, null, groups);

    @Test
    void petWithSearchGroupsIsNotDeleted() {
        when(groups.existsByPetId(7L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> petService.delete(7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Cannot delete a pet with search groups", exception.getReason());
        verifyNoInteractions(pets, ingestion);
    }

    @Test
    void foreignKeyFailureDuringPetDeletionReturnsConflictBeforeCacheEviction() {
        when(pets.findById(7L)).thenReturn(Optional.of(Pet.builder().id(7L).terminalId("tracker-7").build()));
        doThrow(new DataIntegrityViolationException("foreign key violation")).when(pets).flush();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> petService.delete(7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(pets).deleteById(7L);
        verifyNoInteractions(ingestion);
    }

    @Test
    void foreignKeyFailureDuringWalkerAccountDeletionReturnsConflict() {
        DogWalkerProfileRepository profiles = mock(DogWalkerProfileRepository.class);
        UserRepository users = mock(UserRepository.class);
        WalkerMarketplaceService service = new WalkerMarketplaceService(profiles, null, null, null, null, users,
                null, null, null);
        User user = User.builder().id(8L).build();
        DogWalkerProfile profile = DogWalkerProfile.builder().id(5L).user(user).build();
        when(profiles.findById(5L)).thenReturn(Optional.of(profile));
        doThrow(new DataIntegrityViolationException("foreign key violation")).when(users).flush();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.deleteWalkerByAdmin(5L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Cannot delete a walker account with related records", exception.getReason());
        verify(profiles).delete(profile);
        verify(users).delete(user);
    }
}
