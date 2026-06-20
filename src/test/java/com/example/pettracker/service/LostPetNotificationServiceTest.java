package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LostPetNotificationServiceTest {

    @Test
    void notifyPetLostUsesRealtimeAlertWithoutDuplicatingOwnerNotification() {
        NotificationService notificationService = mock(NotificationService.class);
        LostPetNotificationService lostPetNotificationService = new LostPetNotificationService(notificationService);

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

        lostPetNotificationService.notifyPetLost(pet);

        verify(notificationService).sendPhoneNotification(eq("+15551234567"), contains("marked as LOST"));
        verify(notificationService).sendEmail(eq("aldo@example.com"), contains("Milo"), contains("URGENT"));
        verify(notificationService).sendRealtimeAlert(eq(owner), contains("marked as LOST"));
        verify(notificationService, never()).notifyOwner(eq(owner), contains("marked as LOST"));
    }
}
