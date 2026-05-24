package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.Volunteer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VolunteerNotificationService {

    private final VolunteerService volunteerService;
    private final NotificationService notificationService;

    @Async("notificationExecutor")
    public void notifyVolunteers(Pet pet, String alertType) {
        // Call Twilio, Email, or FCM
        List<Volunteer> volunteers = volunteerService.findBySearchGroup_PetIdAndActiveTrue(pet.getId());
        for (Volunteer volunteer : volunteers) {
            if (volunteer.getUser().getEmail() != null && !volunteer.getUser().getEmail().isBlank()) {
                notificationService.sendEmail(volunteer.getUser().getEmail(), "Pet Alert", "Pet " + pet.getName() + " needs assistance: " + alertType);
            }
            if (volunteer.getUser().getPhone() != null && !volunteer.getUser().getPhone().isBlank()) {
                notificationService.sendSms(volunteer.getUser().getPhone(), "Pet " + pet.getName() + " needs assistance: " + alertType);
            }
        }



        System.out.println(Thread.currentThread().getName() +
            " sending " + alertType + " alert for pet " + pet.getName());
    }
}
