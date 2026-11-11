package com.example.pettracker.service;

import com.example.pettracker.dto.AlertDto;
import com.example.pettracker.entity.Alert;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.AlertRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;
    private final CurrentUserService currentUserService;
    private final TemporaryPetAccessService temporaryPetAccessService;

    public AlertService(
            AlertRepository alertRepository,
            CurrentUserService currentUserService,
            TemporaryPetAccessService temporaryPetAccessService) {
        this.alertRepository = alertRepository;
        this.currentUserService = currentUserService;
        this.temporaryPetAccessService = temporaryPetAccessService;
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

    @Transactional(readOnly = true)
    public List<AlertDto> getOpenAlertsForCurrentUser(Authentication authentication) {
        User user = currentUserService.require(authentication);
        List<Alert> alerts = user.getRole() == User.Role.ADMIN
                ? alertRepository.findByStatusOrderByCreatedAtDesc(Alert.Status.OPEN)
                : mergeOwnerAndTemporaryAlerts(user);
        return alerts.stream().map(AlertService::toDto).toList();
    }

    private List<Alert> mergeOwnerAndTemporaryAlerts(User user) {
        List<Alert> ownerAlerts = alertRepository.findByPetOwnerIdAndStatusOrderByCreatedAtDesc(
                user.getId(), Alert.Status.OPEN);
        List<Alert> temporaryAlerts = temporaryPetAccessService.activeAlertPetIds(user).stream()
                .flatMap(petId -> alertRepository.findByPetIdOrderByCreatedAtDesc(petId).stream())
                .filter(alert -> alert.getStatus() == Alert.Status.OPEN)
                .toList();
        return java.util.stream.Stream.concat(ownerAlerts.stream(), temporaryAlerts.stream())
                .sorted(java.util.Comparator.comparing(Alert::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    public void dismissForCurrentUser(Long alertId, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        Pet pet = alert.getPet();
        if (user.getRole() != User.Role.ADMIN
                && (pet == null || pet.getOwner() == null || !user.getId().equals(pet.getOwner().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to dismiss this alert");
        }
        alert.setStatus(Alert.Status.CLOSED);
        alertRepository.save(alert);
    }

    private static AlertDto toDto(Alert alert) {
        Pet pet = alert.getPet();
        return new AlertDto(
                alert.getId(),
                pet == null ? null : pet.getId(),
                pet == null ? null : pet.getName(),
                alert.getAlertType() == null ? null : alert.getAlertType().name(),
                alert.getStatus() == null ? null : alert.getStatus().name(),
                alert.getMetadata(),
                alert.getCreatedAt()
        );
    }
}
