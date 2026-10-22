package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.dto.SearchGroupUpdateRequest;
import com.example.pettracker.dto.VolunteerCandidateDto;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.repository.VolunteerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VolunteerService {

    private final VolunteerRepository volunteerRepository;
    private final SearchGroupRepository searchGroupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Registers a user as a volunteer for a specific search group
     */
    public Volunteer joinSearchGroup(Long userId, Long searchGroupId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        SearchGroup searchGroup = searchGroupRepository.findById(searchGroupId)
            .orElseThrow(() -> new RuntimeException("Search group not found with id: " + searchGroupId));

        Volunteer existingVolunteer = volunteerRepository.findByUserIdAndSearchGroupId(userId, searchGroupId);
        if (existingVolunteer != null && Boolean.TRUE.equals(existingVolunteer.getActive())) {
            log.warn("User {} is already an active volunteer in search group {}", userId, searchGroupId);
            return existingVolunteer;
        }

        Volunteer savedVolunteer = addVolunteerMembership(searchGroup, user);
        log.info("User {} joined search group {} as volunteer", userId, searchGroupId);

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
    public SearchGroup createSearchGroupForLostPet(
            Pet lostPet,
            User petOwner,
            String groupName,
            String description,
            String status,
            String area,
            String city,
            String leaderName,
            String leaderPhone,
            Double coverageRadiusKm,
            List<Long> volunteerUserIds) {
        validateCoverageRadius(coverageRadiusKm);
        SearchGroup searchGroup = SearchGroup.builder()
            .pet(lostPet)
            .createdBy(petOwner)
            .groupName(groupName != null ? groupName : "Search for " + lostPet.getName())
            .description(description)
            .status(status != null ? status : "active")
            .area(area)
            .city(city)
            .leaderName(leaderName != null ? leaderName : petOwner.getName())
            .leaderPhone(leaderPhone != null ? leaderPhone : petOwner.getPhone())
            .coverageRadiusKm(coverageRadiusKm)
            .createdAt(Instant.now())
            .build();

        SearchGroup savedGroup = searchGroupRepository.save(searchGroup);
        addVolunteerMembership(savedGroup, petOwner);
        if (volunteerUserIds != null) {
            volunteerUserIds.stream()
                    .filter(id -> id != null && !id.equals(petOwner.getId()))
                    .distinct()
                    .map(id -> userRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Volunteer not found with id: " + id)))
                    .forEach(user -> {
                        addVolunteerMembership(savedGroup, user);
                        notifyAddedVolunteer(user, savedGroup);
                    });
        }
        log.info("Created search group {} for lost pet {}", savedGroup.getId(), lostPet.getId());

        // Notify nearby volunteers asynchronously
        notifyNearbyVolunteers(lostPet, savedGroup);

        return savedGroup;
    }

    public SearchGroup updateSearchGroup(Long searchGroupId, User actingUser, SearchGroupUpdateRequest request) {
        SearchGroup searchGroup = requireSearchGroup(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        validateCoverageRadius(request.coverageRadiusKm());

        if (request.groupName() != null) {
            if (request.groupName().isBlank()) {
                throw new RuntimeException("groupName cannot be blank");
            }
            searchGroup.setGroupName(request.groupName());
        }
        if (request.description() != null) searchGroup.setDescription(request.description());
        if (request.status() != null) {
            if (request.status().isBlank()) {
                throw new RuntimeException("status cannot be blank");
            }
            searchGroup.setStatus(request.status());
        }
        if (request.area() != null) searchGroup.setArea(request.area());
        if (request.city() != null) searchGroup.setCity(request.city());
        if (request.leaderName() != null) searchGroup.setLeaderName(request.leaderName());
        if (request.leaderPhone() != null) searchGroup.setLeaderPhone(request.leaderPhone());
        if (request.coverageRadiusKm() != null) searchGroup.setCoverageRadiusKm(request.coverageRadiusKm());
        return searchGroupRepository.save(searchGroup);
    }

    public Volunteer addVolunteer(Long searchGroupId, Long userId, User actingUser) {
        SearchGroup searchGroup = requireSearchGroup(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Volunteer volunteer = addVolunteerMembership(searchGroup, user);
        notifyAddedVolunteer(user, searchGroup);
        return volunteer;
    }

    public void removeVolunteer(Long searchGroupId, Long userId, User actingUser) {
        SearchGroup searchGroup = requireSearchGroup(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        if (searchGroup.getCreatedBy() != null && userId.equals(searchGroup.getCreatedBy().getId())) {
            throw new RuntimeException("The group creator cannot be removed");
        }
        leaveSearchGroup(userId, searchGroupId);
    }

    @Transactional(readOnly = true)
    public List<VolunteerCandidateDto> searchVolunteerCandidates(
            Long searchGroupId,
            String query,
            User actingUser,
            Pageable pageable) {
        SearchGroup searchGroup = requireSearchGroup(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new RuntimeException("q must contain at least 2 characters");
        }
        if (normalizedQuery.length() > 100) {
            throw new RuntimeException("q cannot exceed 100 characters");
        }
        return userRepository.searchVolunteerCandidates(searchGroupId, normalizedQuery, pageable);
    }

    private Volunteer addVolunteerMembership(SearchGroup searchGroup, User user) {
        Volunteer volunteer = volunteerRepository.findByUserIdAndSearchGroupId(user.getId(), searchGroup.getId());
        if (volunteer == null) {
            volunteer = Volunteer.builder()
                    .user(user)
                    .searchGroup(searchGroup)
                    .active(true)
                    .joinedAt(Instant.now())
                    .build();
        } else {
            volunteer.setActive(true);
            volunteer.setJoinedAt(Instant.now());
        }
        Volunteer savedVolunteer = volunteerRepository.save(volunteer);
        searchGroup.getVolunteers().add(user);
        searchGroupRepository.save(searchGroup);
        return savedVolunteer;
    }

    private SearchGroup requireSearchGroup(Long searchGroupId) {
        return searchGroupRepository.findById(searchGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found"));
    }

    private void requireGroupManager(User actingUser, SearchGroup searchGroup) {
        boolean admin = actingUser != null && actingUser.getRole() == User.Role.ADMIN;
        boolean creator = actingUser != null && searchGroup.getCreatedBy() != null
                && actingUser.getId().equals(searchGroup.getCreatedBy().getId());
        boolean petOwner = actingUser != null && searchGroup.getPet() != null && searchGroup.getPet().getOwner() != null
                && actingUser.getId().equals(searchGroup.getPet().getOwner().getId());
        if (!admin && !creator && !petOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the group owner or an admin can manage this group");
        }
    }

    private void validateCoverageRadius(Double coverageRadiusKm) {
        if (coverageRadiusKm != null && coverageRadiusKm < 0) {
            throw new RuntimeException("coverageRadiusKm cannot be negative");
        }
    }

    private void notifyAddedVolunteer(User volunteer, SearchGroup searchGroup) {
        String message = "You were added to search group '" + searchGroup.getGroupName() + "'";
        if (volunteer.getPhone() != null && !volunteer.getPhone().isBlank()) {
            notificationService.sendPhoneNotification(volunteer.getPhone(), message);
        }
        if (volunteer.getEmail() != null && !volunteer.getEmail().isBlank()) {
            notificationService.sendEmail(volunteer.getEmail(), "Pet search group", message);
        }
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
                notificationService.sendPhoneNotification(volunteer.getPhone(), updateMessage);
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
                    notificationService.sendPhoneNotification(volunteerUser.getPhone(), message);
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
            notificationService.sendPhoneNotification(petOwner.getPhone(), message);
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

    public List<Volunteer> getActiveVolunteersForGroup(Long searchGroupId, Pageable pageable) {
        return volunteerRepository.findBySearchGroupIdAndActiveTrue(searchGroupId, pageable);
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

    public List<SearchGroup> getUserSearchGroups(Long userId, Pageable pageable) {
        List<Volunteer> volunteers = volunteerRepository.findByUserIdAndActiveTrue(userId, pageable);
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
