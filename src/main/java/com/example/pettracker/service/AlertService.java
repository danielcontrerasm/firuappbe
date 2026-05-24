package com.example.pettracker.service;

import com.example.pettracker.entity.Alert;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.AlertRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public Alert createAlert(Pet pet, Alert.AlertType alertType, String metadata) {
        Alert alert = Alert.builder()
                .pet(pet)
                .alertType(alertType)
                .status(Alert.Status.OPEN)
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public List<Alert> getAlertsForPet(Pet pet) {
        return alertRepository.findByPet(pet);
    }

    @Transactional(readOnly = true)
    public List<Alert> getAlertsForPetId(Long petId) {
        return alertRepository.findByPetIdOrderByCreatedAtDesc(petId);
    }

    public Alert closeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + alertId));
        alert.setStatus(Alert.Status.CLOSED);
        return alertRepository.save(alert);
    }
}
