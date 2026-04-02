package com.example.pettracker.service;

import com.example.pettracker.model.Alert;
import com.example.pettracker.model.AlertType;
import com.example.pettracker.model.Geofence;
import com.example.pettracker.model.Pet;
import com.example.pettracker.model.PetLocation;
import com.example.pettracker.repository.AlertRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public void createLostAlert(Pet pet) {
        createAlert(pet, AlertType.PET_MARKED_LOST, pet.getName() + " has been marked as lost.");
    }

    public void createGeofenceAlert(Pet pet, PetLocation location, Geofence geofence, double distanceMeters) {
        String message = "%s is outside the safe area by %.0f meters at %.5f, %.5f."
                .formatted(pet.getName(), Math.max(distanceMeters - geofence.getRadiusMeters(), 0), location.getLatitude(), location.getLongitude());
        createAlert(pet, AlertType.GEOFENCE_BREACH, message);
    }

    private void createAlert(Pet pet, AlertType type, String message) {
        Alert alert = new Alert();
        alert.setPet(pet);
        alert.setType(type);
        alert.setMessage(message);
        alert.setCreatedAt(Instant.now());
        alertRepository.save(alert);
    }
}
