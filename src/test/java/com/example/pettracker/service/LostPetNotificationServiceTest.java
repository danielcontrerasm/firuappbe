package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LostPetNotificationServiceTest {

    @Test
    void notifyPetLostNotifiesOwnerAndSearchGroupsOnce() {
        NotificationService notificationService = mock(NotificationService.class);
        PetRepository petRepository = mock(PetRepository.class);
        VolunteerNotificationService volunteerNotificationService = mock(VolunteerNotificationService.class);
        LostPetNotificationService lostPetNotificationService = new LostPetNotificationService(
                notificationService, petRepository, volunteerNotificationService);

        User owner = User.builder()
                .id(7L)
                .name("Aldo")
                .email("aldo@example.com")
                .phone("+15551234567")
                .build();

        Pet pet = Pet.builder()
                .id(12L)
                .name("Milo")
                .type("dog")
                .owner(owner)
                .build();

        when(petRepository.findById(12L)).thenReturn(Optional.of(pet));

        lostPetNotificationService.notifyPetLost(12L);

        verify(notificationService).notifyLostPet(eq(owner), eq("Milo"), eq("dog"), contains("Pet ID: 12"));
        verify(volunteerNotificationService).notifyPetGroupMembers(pet, "LOST");
        verifyNoMoreInteractions(notificationService, volunteerNotificationService);
    }

    @Test
    void notifyPetLostSkipsMissingPet() {
        NotificationService notificationService = mock(NotificationService.class);
        PetRepository petRepository = mock(PetRepository.class);
        VolunteerNotificationService volunteerNotificationService = mock(VolunteerNotificationService.class);
        LostPetNotificationService lostPetNotificationService = new LostPetNotificationService(
                notificationService, petRepository, volunteerNotificationService);
        when(petRepository.findById(12L)).thenReturn(Optional.empty());

        lostPetNotificationService.notifyPetLost(12L);

        verifyNoInteractions(notificationService, volunteerNotificationService);
    }
}
