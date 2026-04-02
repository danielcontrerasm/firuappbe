package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.repository.VolunteerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VolunteerService {

    private final VolunteerRepository volunteerRepository;
    private final SearchGroupRepository searchGroupRepository;
    private final UserRepository userRepository;
    private final VolunteerNotificationService volunteerNotificationService;
    private final NotificationService notificationService;

    /**
     * Registers a user as a volunteer for a specific search group
     */
    public Volunteer joinSearchGroup(Long userId, Long searchGroupId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        SearchGroup searchGroup = searchGroupRepository.findById(searchGroupId)
            .orElseThrow(() -> new RuntimeException("Search group not found with id: " + searchGroupId));

        // Check if already a volunteer in this group
        Volunteer existingVolunteer = volunteerRepository
            .findByUserIdAndSearchGroupId(userId, searchGroupId);

        if (existingVolunteer != null && existingVolunteer.getActive()) {
            log.warn("User {} is already an active volunteer in search group {}", userId, searchGroupId);
            return existingVolunteer;
        }

        Volunteer volunteer = Volunteer.builder()
            .user(user)
            .searchGroup(searchGroup)
            .active(true)
            .joinedAt(Instant.now())
            .build();

        Volunteer savedVolunteer = volunteerRepository.save(volunteer);
        log.info("User {} joined search group {} as volunteer", userId, searchGroupId);

        // Add volunteer to search group's volunteer list
        searchGroup.getVolunteers().add(user);
        searchGroupRepository.save(searchGroup);

        // Send confirmation notification
        sendVolunteerJoinedNotification(user, searchGroup);

        return savedVolunteer;
    }

    /**
     * Removes a volunteer from a search group
     */
    public void leaveSearchGroup(Long userId, Long searchGroupId) {
        Volunteer volunteer = volunteerRepository
            .findByUserIdAndSearchGroupId(userId, searchGroupId);

        if (volunteer == null) {
            throw new RuntimeException("Volunteer not found");
        }

        volunteer.setActive(false);
        volunteerRepository.save(volunteer);

        SearchGroup searchGroup = searchGroupRepository.findById(searchGroupId).orElse(null);
        if (searchGroup != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                searchGroup.getVolunteers().remove(user);
                searchGroupRepository.save(searchGroup);
            }
        }

        log.info("User {} left search group {}", userId, searchGroupId);
    }

    /**
     * Creates a new search group for a lost pet and notifies nearby volunteers
     */
    public SearchGroup createSearchGroupForLostPet(Pet lostPet, User petOwner, String groupName) {
        SearchGroup searchGroup = SearchGroup.builder()
            .pet(lostPet)
            .createdBy(petOwner)
            .groupName(groupName != null ? groupName : "Search for " + lostPet.getName())
            .createdAt(Instant.now())
            .build();

        SearchGroup savedGroup = searchGroupRepository.save(searchGroup);
        log.info("Created search group {} for lost pet {}", savedGroup.getId(), lostPet.getId());

        // Notify nearby volunteers asynchronously
        notifyNearbyVolunteers(lostPet, savedGroup);

        return savedGroup;
    }

    /**
     * Notifies all active volunteers in a search group about pet status updates
     */
    @Async("notificationExecutor")
    public void notifyVolunteersInGroup(SearchGroup searchGroup, String updateMessage) {
        if (searchGroup == null || searchGroup.getVolunteers().isEmpty()) {
            log.debug("No volunteers to notify for search group {}", searchGroup != null ? searchGroup.getId() : "null");
            return;
        }

        for (User volunteer : searchGroup.getVolunteers()) {
            if (volunteer.getPhone() != null && !volunteer.getPhone().isBlank()) {
                notificationService.sendSms(volunteer.getPhone(), updateMessage);
            }
            if (volunteer.getEmail() != null && !volunteer.getEmail().isBlank()) {
                String subject = "Update: " + (searchGroup.getPet() != null ? searchGroup.getPet().getName() : "Search") + " Search";
                notificationService.sendEmail(volunteer.getEmail(), subject, updateMessage);
            }
        }

        log.info("Notified {} volunteers in search group {}", searchGroup.getVolunteers().size(), searchGroup.getId());
    }

    /**
     * Notifies nearby volunteers about a newly lost pet
     */
    @Async("notificationExecutor")
    public void notifyNearbyVolunteers(Pet lostPet, SearchGroup searchGroup) {
        if (lostPet == null || lostPet.getOwner() == null) {
            log.warn("Invalid pet or owner for volunteer notification");
            return;
        }

        // Get all active volunteers (in production, you'd filter by location)
        List<Volunteer> activeVolunteers = volunteerRepository.findByActiveTrueAndSearchGroupId(searchGroup.getId());

        String petName = lostPet.getName() != null ? lostPet.getName() : "a pet";
        String petType = lostPet.getType() != null ? lostPet.getType() : "animal";

        String message = String.format(
            "A %s named '%s' has been reported lost nearby! " +
            "Click the app to join the search group and help reunite it with its owner.",
            petType, petName
        );

        String emailSubject = "Help Needed: Lost " + petType + " Named '" + petName + "'";

        String emailBody = String.format(
            "Dear Volunteer,\n\n" +
            "A %s named '%s' has been reported as LOST in your area!\n\n" +
            "Pet Information:\n" +
            "- Name: %s\n" +
            "- Type: %s\n" +
            "- Owner: %s\n" +
            "- Owner Contact: %s\n\n" +
            "We need your help! If you'd like to join the search:\n" +
            "1. Open the PetTracker app\n" +
            "2. Find the search group for '%s'\n" +
            "3. Click 'Join Search Group'\n" +
            "4. Start searching and share any sightings\n\n" +
            "Every second counts. Thank you for helping!\n\n" +
            "Best regards,\n" +
            "PetTracker Team",
            petType, petName, petName, petType, lostPet.getOwner().getName(),
            lostPet.getOwner().getEmail(), petName
        );

        // Send notifications to all active volunteers
        for (Volunteer volunteer : activeVolunteers) {
            User volunteerUser = volunteer.getUser();
            if (volunteerUser == null) continue;

            try {
                if (volunteerUser.getPhone() != null && !volunteerUser.getPhone().isBlank()) {
                    notificationService.sendSms(volunteerUser.getPhone(), message);
                }
                if (volunteerUser.getEmail() != null && !volunteerUser.getEmail().isBlank()) {
                    notificationService.sendEmail(volunteerUser.getEmail(), emailSubject, emailBody);
                }
                log.info("Notified volunteer {} about lost pet {}", volunteerUser.getId(), lostPet.getId());
            } catch (Exception e) {
                log.error("Failed to notify volunteer {} about lost pet {}", volunteerUser.getId(), lostPet.getId(), e);
            }
        }

        log.info("Notified {} volunteers about lost pet {}", activeVolunteers.size(), lostPet.getId());
    }

    /**
     * Sends notification when a volunteer joins a search group
     */
    @Async("notificationExecutor")
    private void sendVolunteerJoinedNotification(User volunteer, SearchGroup searchGroup) {
        if (searchGroup.getPet() == null || searchGroup.getPet().getOwner() == null) {
            return;
        }

        User petOwner = searchGroup.getPet().getOwner();
        String volunteerName = volunteer.getName() != null ? volunteer.getName() : "A volunteer";
        String petName = searchGroup.getPet().getName() != null ? searchGroup.getPet().getName() : "your pet";

        String message = String.format(
            "%s has joined the search group for %s! Thank you for the help.",
            volunteerName, petName
        );

        String emailBody = String.format(
            "Dear %s,\n\n" +
            "Good news! %s has joined the search group for '%s'.\n\n" +
            "Volunteer Information:\n" +
            "- Name: %s\n" +
            "- Email: %s\n\n" +
            "You can now coordinate search efforts with them through the app.\n\n" +
            "Every helper brings your pet closer to home!\n\n" +
            "Best regards,\n" +
            "PetTracker Team",
            petOwner.getName(), volunteerName, petName, volunteer.getName(), volunteer.getEmail()
        );

        if (petOwner.getPhone() != null && !petOwner.getPhone().isBlank()) {
            notificationService.sendSms(petOwner.getPhone(), message);
        }
        if (petOwner.getEmail() != null && !petOwner.getEmail().isBlank()) {
            notificationService.sendEmail(petOwner.getEmail(), "New Volunteer Joined the Search", emailBody);
        }

        log.info("Notified pet owner about volunteer {} joining search group", volunteer.getId());
    }

    /**
     * Gets all active volunteers for a specific search group
     */
    public List<Volunteer> getActiveVolunteersForGroup(Long searchGroupId) {
        return volunteerRepository.findBySearchGroupIdAndActiveTrue(searchGroupId);
    }

    /**
     * Gets all search groups a user is volunteering for
     */
    public List<SearchGroup> getUserSearchGroups(Long userId) {
        List<Volunteer> volunteers = volunteerRepository.findByUserIdAndActiveTrue(userId);
        return volunteers.stream()
            .map(Volunteer::getSearchGroup)
            .collect(Collectors.toList());
    }

    /**
     * Gets volunteer statistics for a search group
     */
    public long getActiveVolunteerCount(Long searchGroupId) {
        return volunteerRepository.countBySearchGroupIdAndActiveTrue(searchGroupId);
    }

    /**
     * Deactivates all volunteers for a search group (e.g., when pet is found)
     */
    public void deactivateSearchGroupVolunteers(Long searchGroupId) {
        List<Volunteer> volunteers = volunteerRepository.findBySearchGroupId(searchGroupId);
        for (Volunteer volunteer : volunteers) {
            volunteer.setActive(false);
        }
        volunteerRepository.saveAll(volunteers);
        log.info("Deactivated all volunteers for search group {}", searchGroupId);
    }

    public List<Volunteer> findBySearchGroup_PetIdAndActiveTrue(Long id) {
       return  volunteerRepository.findBySearchGroup_PetId(id);
    }
}
