
package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LostPetNotificationService {

    private final NotificationService notificationService;

    /**
     * Sends notifications to pet owner when their pet is marked as lost
     */
    @Async("notificationExecutor")
    public void notifyPetLost(Pet pet) {
        if (pet == null || pet.getOwner() == null) {
            log.warn("Invalid pet or owner for lost pet notification");
            return;
        }

        User owner = pet.getOwner();
        String petName = pet.getName() != null ? pet.getName() : "Your pet";
        String petType = pet.getType() != null ? pet.getType() : "pet";

        String message = String.format(
            "ALERT: %s (%s) has been marked as LOST. " +
            "Please contact authorities and check your email for more information.",
            petName, petType
        );

        String emailBody = String.format(
            "Dear %s,\n\n" +
            "URGENT: Your %s '%s' has been marked as LOST.\n\n" +
            "Pet Details:\n" +
            "- Name: %s\n" +
            "- Type: %s\n" +
            "- Pet ID: %s\n\n" +
            "Please take immediate action:\n" +
            "1. Check your pet's last known location in the app\n" +
            "2. Contact local animal shelters and authorities\n" +
            "3. Share this information with nearby volunteers\n" +
            "4. Check the app regularly for updates from search volunteers\n\n" +
            "We're here to help. Stay strong!\n\n" +
            "Best regards,\n" +
            "PetTracker Team",
            owner.getName(),
            petType,
            petName,
            petName,
            petType,
            pet.getId()
        );

        // Send phone notification
        if (owner.getPhone() != null && !owner.getPhone().isBlank()) {
            notificationService.sendPhoneNotification(owner.getPhone(), message);
            log.info("Phone alert sent to owner {} for lost pet {}", owner.getId(), pet.getId());
        }

        // Send Email notification
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            notificationService.sendEmail(
                owner.getEmail(),
                "URGENT: Your Pet " + petName + " Has Been Marked as LOST",
                emailBody
            );
            log.info("Email sent to owner {} for lost pet {}", owner.getId(), pet.getId());
        }

        // Send real-time WebSocket notification
        notificationService.sendRealtimeAlert(owner, message);
        log.info("All notifications sent for lost pet: {}", pet.getId());
    }

    /**
     * Sends notification when a lost pet is found
     */
    @Async("notificationExecutor")
    public void notifyPetFound(Pet pet) {
        if (pet == null || pet.getOwner() == null) {
            log.warn("Invalid pet or owner for found pet notification");
            return;
        }

        User owner = pet.getOwner();
        String petName = pet.getName() != null ? pet.getName() : "Your pet";
        String petType = pet.getType() != null ? pet.getType() : "pet";

        String message = String.format(
            "GOOD NEWS: %s (%s) has been FOUND! Check the app for details.",
            petName, petType
        );

        String emailBody = String.format(
            "Dear %s,\n\n" +
            "WONDERFUL NEWS: Your %s '%s' has been FOUND!\n\n" +
            "Pet Details:\n" +
            "- Name: %s\n" +
            "- Type: %s\n" +
            "- Pet ID: %s\n\n" +
            "Please check the app immediately for:\n" +
            "- Current location of your pet\n" +
            "- Contact information of the finder\n" +
            "- Next steps to retrieve your pet\n\n" +
            "We're thrilled to help reunite you with your beloved companion!\n\n" +
            "Best regards,\n" +
            "PetTracker Team",
            owner.getName(),
            petType,
            petName,
            petName,
            petType,
            pet.getId()
        );

        // Send phone notification
        if (owner.getPhone() != null && !owner.getPhone().isBlank()) {
            notificationService.sendPhoneNotification(owner.getPhone(), message);
            log.info("Phone alert sent to owner {} - pet {} found", owner.getId(), pet.getId());
        }

        // Send Email notification
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            notificationService.sendEmail(
                owner.getEmail(),
                "GREAT NEWS: Your Pet " + petName + " Has Been Found!",
                emailBody
            );
            log.info("Email sent to owner {} - pet {} found", owner.getId(), pet.getId());
        }

        // Send real-time WebSocket notification
        notificationService.sendRealtimeAlert(owner, message);
        log.info("All notifications sent - pet found: {}", pet.getId());
    }
}
