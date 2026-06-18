package com.example.pettracker.transit.service;

import com.example.pettracker.entity.User;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.service.NotificationService;
import com.example.pettracker.transit.dto.TransitSearchGroupCreateRequest;
import com.example.pettracker.transit.entity.TransitSearchGroup;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.entity.TransitVolunteer;
import com.example.pettracker.transit.repository.TransitSearchGroupRepository;
import com.example.pettracker.transit.repository.TransitVolunteerRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransitSearchGroupService {

    private final TransitSearchGroupRepository transitSearchGroupRepository;
    private final TransitVolunteerRepository transitVolunteerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public TransitSearchGroupService(
            TransitSearchGroupRepository transitSearchGroupRepository,
            TransitVolunteerRepository transitVolunteerRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.transitSearchGroupRepository = transitSearchGroupRepository;
        this.transitVolunteerRepository = transitVolunteerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public TransitSearchGroup create(TransitVehicle vehicle, User creator, TransitSearchGroupCreateRequest request) {
        TransitSearchGroup group = TransitSearchGroup.builder()
                .vehicle(vehicle)
                .createdBy(creator)
                .groupName(request.groupName() == null || request.groupName().isBlank()
                        ? "Search for " + vehicle.getLabel()
                        : request.groupName())
                .description(request.description())
                .status(request.status() == null || request.status().isBlank() ? "active" : request.status())
                .area(request.area())
                .city(request.city())
                .coverageRadiusKm(request.coverageRadiusKm())
                .leaderName(request.leaderName() == null || request.leaderName().isBlank() ? creator.getName() : request.leaderName())
                .leaderPhone(request.leaderPhone() == null || request.leaderPhone().isBlank() ? creator.getPhone() : request.leaderPhone())
                .incidentType(request.incidentType())
                .build();
        TransitSearchGroup savedGroup = transitSearchGroupRepository.save(group);
        addVolunteer(savedGroup, creator);

        List<Long> additionalVolunteerIds = request.volunteerUserIds() == null
                ? List.of()
                : new ArrayList<>(request.volunteerUserIds());
        for (Long volunteerId : additionalVolunteerIds) {
            if (volunteerId == null || volunteerId.equals(creator.getId())) {
                continue;
            }
            User volunteer = userRepository.findById(volunteerId)
                    .orElseThrow(() -> new RuntimeException("Volunteer not found with id: " + volunteerId));
            addVolunteer(savedGroup, volunteer);
            notifyVolunteer(volunteer, savedGroup);
        }
        return savedGroup;
    }

    public List<TransitSearchGroup> listForVehicle(Long vehicleId) {
        return transitSearchGroupRepository.findByVehicleId(vehicleId);
    }

    public List<TransitSearchGroup> listForUser(Long userId) {
        return transitVolunteerRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(TransitVolunteer::getSearchGroup)
                .distinct()
                .toList();
    }

    public TransitSearchGroup findById(Long id) {
        return transitSearchGroupRepository.findById(id).orElse(null);
    }

    public TransitVolunteer joinSearchGroup(Long userId, Long searchGroupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        TransitSearchGroup searchGroup = transitSearchGroupRepository.findById(searchGroupId)
                .orElseThrow(() -> new RuntimeException("Search group not found with id: " + searchGroupId));
        TransitVolunteer existingVolunteer = transitVolunteerRepository.findByUserIdAndSearchGroupId(userId, searchGroupId);
        if (existingVolunteer != null && Boolean.TRUE.equals(existingVolunteer.getActive())) {
            return existingVolunteer;
        }
        return addVolunteer(searchGroup, user);
    }

    public void leaveSearchGroup(Long userId, Long searchGroupId) {
        TransitVolunteer volunteer = transitVolunteerRepository.findByUserIdAndSearchGroupId(userId, searchGroupId);
        if (volunteer == null) {
            throw new RuntimeException("Volunteer not found");
        }
        volunteer.setActive(false);
        transitVolunteerRepository.save(volunteer);
        TransitSearchGroup searchGroup = volunteer.getSearchGroup();
        if (searchGroup != null && searchGroup.getVolunteers() != null) {
            searchGroup.getVolunteers().removeIf(user -> user.getId().equals(userId));
            transitSearchGroupRepository.save(searchGroup);
        }
    }

    private TransitVolunteer addVolunteer(TransitSearchGroup searchGroup, User user) {
        TransitVolunteer existingVolunteer = transitVolunteerRepository.findByUserIdAndSearchGroupId(user.getId(), searchGroup.getId());
        if (existingVolunteer != null) {
            existingVolunteer.setActive(true);
            TransitVolunteer savedVolunteer = transitVolunteerRepository.save(existingVolunteer);
            searchGroup.getVolunteers().add(user);
            transitSearchGroupRepository.save(searchGroup);
            return savedVolunteer;
        }

        TransitVolunteer savedVolunteer = transitVolunteerRepository.save(TransitVolunteer.builder()
                .user(user)
                .searchGroup(searchGroup)
                .build());
        searchGroup.getVolunteers().add(user);
        transitSearchGroupRepository.save(searchGroup);
        return savedVolunteer;
    }

    private void notifyVolunteer(User volunteer, TransitSearchGroup group) {
        String message = "You were added to transit search group '" + group.getGroupName() + "' for vehicle "
                + group.getVehicle().getLabel();
        if (volunteer.getPhone() != null && !volunteer.getPhone().isBlank()) {
            notificationService.sendSms(volunteer.getPhone(), message);
        }
        if (volunteer.getEmail() != null && !volunteer.getEmail().isBlank()) {
            notificationService.sendEmail(volunteer.getEmail(), "Transit search group", message);
        }
    }
}
