package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.entity.VolunteerRejoinRequest;
import com.example.pettracker.event.SearchGroupCreatedEvent;
import com.example.pettracker.event.VolunteerJoinedGroupEvent;
import com.example.pettracker.event.VolunteerAddedToGroupEvent;
import com.example.pettracker.event.VolunteerRejoinRequestedEvent;
import com.example.pettracker.event.VolunteerRejoinResolvedEvent;
import com.example.pettracker.repository.VolunteerRepository;
import com.example.pettracker.repository.VolunteerRejoinRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VolunteerNotificationService {

    private final NotificationService notificationService;
    private final VolunteerRepository volunteerRepository;
    private final VolunteerRejoinRequestRepository rejoinRequestRepository;

    // REVIEWED
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true)
    public void notifyGroupMembersWhenCreated(SearchGroupCreatedEvent event) {
        List<Volunteer> members = volunteerRepository
                .findByActiveTrueAndSearchGroupId(event.searchGroupId());
        String pet = event.petName() == null || event.petName().isBlank() ? "your pet" : event.petName();
        String petType = event.petType() == null || event.petType().isBlank() ? "pet" : event.petType();
        String subject = "Volunteer group created: " + event.groupName();
        String message = "You are a member of the volunteer group '" + event.groupName() + "' for "
                + petType + " " + pet + "."
                + (event.area() == null || event.area().isBlank() ? "" : " Area: " + event.area())
                + (event.city() == null || event.city().isBlank() ? "" : ", " + event.city())
                + " Open FiruApp to coordinate with the group.";

        for (Volunteer membership : members) {
            var user = membership.getUser();
            if (user == null) {
                continue;
            }
            try {
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    notificationService.sendEmail(user.getEmail(), subject, message);
                }
                if (user.getPhone() != null && !user.getPhone().isBlank()) {
                    notificationService.sendPhoneNotification(user.getPhone(), message);
                }
            } catch (Exception exception) {
                log.warn("Could not notify group member userId={} searchGroupId={}",
                        user.getId(), event.searchGroupId(), exception);
            }
        }
        log.info("Sent group-created notifications to {} active members of search group {}",
                members.size(), event.searchGroupId());
    }

    // REVIEWED
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true)
    public void notifyOwnerWhenVolunteerJoins(VolunteerJoinedGroupEvent event) {
        Volunteer membership = volunteerRepository.findByUserIdAndSearchGroupId(
                event.volunteerUserId(), event.searchGroupId());
        if (membership == null || !Boolean.TRUE.equals(membership.getActive())
                || membership.getUser() == null || membership.getSearchGroup() == null) {
            return;
        }

        SearchGroup group = membership.getSearchGroup();
        Pet pet = group.getPet();
        User volunteer = membership.getUser();
        User owner = pet == null ? null : pet.getOwner();
        if (owner == null) {
            return;
        }

        String volunteerName = volunteer.getName() == null ? "A volunteer" : volunteer.getName();
        String petName = pet.getName() == null ? "your pet" : pet.getName();
        String message = volunteerName + " has joined the volunteer group '" + group.getGroupName()
                + "' for " + petName + ". Thank you for the help.";
        String emailBody = "Hello " + (owner.getName() == null ? "" : owner.getName()) + ",\n\n"
                + message + "\n\nYou can coordinate with the volunteer in FiruApp.";

        try {
            if (owner.getPhone() != null && !owner.getPhone().isBlank()) {
                notificationService.sendPhoneNotification(owner.getPhone(), message);
            }
            if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                notificationService.sendEmail(owner.getEmail(), "New volunteer joined your group", emailBody);
            }
            log.info("Notified group owner userId={} after volunteer userId={} joined groupId={}",
                    owner.getId(), volunteer.getId(), group.getId());
        } catch (Exception exception) {
            log.warn("Could not notify group owner after volunteer joined groupId={}", group.getId(), exception);
        }
    }

    // REVIEWED
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true)
    public void notifyVolunteerWhenAdded(VolunteerAddedToGroupEvent event) {
        Volunteer membership = volunteerRepository.findByUserIdAndSearchGroupId(
                event.volunteerUserId(), event.searchGroupId());
        if (membership == null || !Boolean.TRUE.equals(membership.getActive())
                || membership.getUser() == null || membership.getSearchGroup() == null) {
            return;
        }

        User volunteer = membership.getUser();
        SearchGroup group = membership.getSearchGroup();
        String message = "You were added to search group '" + group.getGroupName() + "'.";
        try {
            if (volunteer.getPhone() != null && !volunteer.getPhone().isBlank()) {
                notificationService.sendPhoneNotification(volunteer.getPhone(), message);
            }
            if (volunteer.getEmail() != null && !volunteer.getEmail().isBlank()) {
                notificationService.sendEmail(volunteer.getEmail(), "Pet search group", message);
            }
        } catch (Exception exception) {
            log.warn("Could not notify added volunteer userId={} searchGroupId={}",
                    volunteer.getId(), group.getId(), exception);
        }
    }

    // REVIEWED
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true)
    public void notifyCreatorAboutRejoinRequest(VolunteerRejoinRequestedEvent event) {
        VolunteerRejoinRequest request = rejoinRequestRepository
                .findWithDetailsById(event.requestId())
                .orElse(null);
        if (request == null || request.getStatus() != VolunteerRejoinRequest.Status.PENDING
                || request.getSearchGroup().getCreatedBy() == null) {
            return;
        }

        User creator = request.getSearchGroup().getCreatedBy();
        User volunteer = request.getVolunteer().getUser();
        if (volunteer == null) {
            return;
        }
        String volunteerName = volunteer.getName() == null || volunteer.getName().isBlank()
                ? "Un voluntario"
                : volunteer.getName();
        String groupName = request.getSearchGroup().getGroupName();
        String message = volunteerName + " solicita volver al grupo '" + groupName
                + "'. Revisa las solicitudes pendientes en FiruApp."
                + " Solicitud: " + request.getId()
                + ". Endpoints: GET /api/search-groups/" + request.getSearchGroup().getId()
                + "/rejoin-requests; POST /api/search-groups/" + request.getSearchGroup().getId()
                + "/rejoin-requests/" + request.getId() + "/approve o /reject.";
        try {
            if (creator.getEmail() != null && !creator.getEmail().isBlank()) {
                notificationService.sendEmail(creator.getEmail(), "Solicitud para volver al grupo " + groupName, message);
            }
            if (creator.getPhone() != null && !creator.getPhone().isBlank()) {
                notificationService.sendPhoneNotification(creator.getPhone(), message);
            }
        } catch (Exception exception) {
            log.warn("Could not notify creator about rejoin requestId={} groupId={}",
                    request.getId(), request.getSearchGroup().getId(), exception);
        }
    }

    // REVIEWED
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true)
    public void notifyVolunteerAboutRejoinDecision(VolunteerRejoinResolvedEvent event) {
        VolunteerRejoinRequest request = rejoinRequestRepository
                .findWithDetailsById(event.requestId())
                .orElse(null);
        if (request == null || request.getVolunteer().getUser() == null) {
            return;
        }

        User volunteer = request.getVolunteer().getUser();
        String groupName = request.getSearchGroup().getGroupName();
        boolean approved = request.getStatus() == VolunteerRejoinRequest.Status.APPROVED;
        String message = approved
                ? "Tu solicitud fue aprobada. Ya eres miembro activo del grupo '" + groupName + "'."
                : "El creador rechazó tu solicitud para volver al grupo '" + groupName + "'.";
        try {
            if (volunteer.getEmail() != null && !volunteer.getEmail().isBlank()) {
                notificationService.sendEmail(
                        volunteer.getEmail(),
                        approved ? "Solicitud de reingreso aprobada" : "Solicitud de reingreso rechazada",
                        message);
            }
            if (volunteer.getPhone() != null && !volunteer.getPhone().isBlank()) {
                notificationService.sendPhoneNotification(volunteer.getPhone(), message);
            }
        } catch (Exception exception) {
            log.warn("Could not notify volunteer about rejoin decision requestId={}", request.getId(), exception);
        }
    }

    // REVIEWED
    @Async("notificationExecutor")
    @Transactional(readOnly = true)
    public void notifyPetGroupMembers(Pet pet, String alertType) {
        if (pet == null || pet.getId() == null) {
            return;
        }
        List<Volunteer> memberships = volunteerRepository
                .findActiveMembersOfActiveGroupsByPetId(pet.getId());
        if (memberships.isEmpty()) {
            log.debug("No active group members to notify for pet {}", pet.getId());
            return;
        }

        String petName = pet.getName() == null || pet.getName().isBlank() ? "your pet" : pet.getName();
        String eventMessage = "LOST".equalsIgnoreCase(alertType)
                ? petName + " has been marked as lost. Please open FiruApp to coordinate with your group."
                : petName + " triggered a geofence alert. Please open FiruApp to coordinate with your group.";

        for (Volunteer membership : memberships) {
            var user = membership.getUser();
            SearchGroup group = membership.getSearchGroup();
            if (user == null || group == null) {
                continue;
            }
            String message = "Group '" + group.getGroupName() + "': " + eventMessage;
            String subject = "LOST".equalsIgnoreCase(alertType)
                    ? "Lost pet update: " + petName
                    : "Geofence alert: " + petName;
            try {
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    notificationService.sendEmail(user.getEmail(), subject, message);
                }
                if (user.getPhone() != null && !user.getPhone().isBlank()) {
                    notificationService.sendPhoneNotification(user.getPhone(), message);
                }
            } catch (Exception exception) {
                log.warn("Could not notify member userId={} searchGroupId={}",
                        user.getId(), group.getId(), exception);
            }
        }
        log.info("Notified {} active group memberships for pet {} after {}", memberships.size(), pet.getId(), alertType);
    }
}
