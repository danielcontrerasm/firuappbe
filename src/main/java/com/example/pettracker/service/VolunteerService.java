package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.entity.VolunteerRejoinRequest;
import com.example.pettracker.entity.VolunteerRejoinRequest.Status;
import com.example.pettracker.event.SearchGroupCreatedEvent;
import com.example.pettracker.event.VolunteerJoinedGroupEvent;
import com.example.pettracker.event.VolunteerAddedToGroupEvent;
import com.example.pettracker.event.VolunteerRejoinRequestedEvent;
import com.example.pettracker.event.VolunteerRejoinResolvedEvent;
import com.example.pettracker.dto.SearchGroupUpdateRequest;
import com.example.pettracker.dto.SearchGroupCreationCommand;
import com.example.pettracker.dto.VolunteerCandidateDto;
import com.example.pettracker.dto.VolunteerRejoinRequestDto;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.repository.VolunteerRepository;
import com.example.pettracker.repository.VolunteerRejoinRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VolunteerService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final VolunteerRepository volunteerRepository;
    private final VolunteerRejoinRequestRepository rejoinRequestRepository;
    private final SearchGroupRepository searchGroupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ImageUploadLimits imageUploadLimits;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Registers a user as a volunteer for a specific search group
     */
    // REVIEWED
    public Volunteer joinSearchGroup(Long userId, Long searchGroupId) {
        return joinSearchGroupWithResult(userId, searchGroupId).volunteer();
    }

    /**
     * Registers a user as a volunteer and reports whether the membership was
     * created, reactivated, or already active.
     */
    // REVIEWED
    public JoinSearchGroupResult joinSearchGroupWithResult(Long userId, Long searchGroupId) {
        log.info("Join search group request userId={} searchGroupId={}", userId, searchGroupId);

        // Find user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        Volunteer existingVolunteer = volunteerRepository.findByUserIdAndSearchGroupId(userId, searchGroupId);
        boolean revoked = existingVolunteer != null && Boolean.TRUE.equals(existingVolunteer.getRevoked());
        boolean active = isActiveMember(existingVolunteer);
        if (!"PUBLIC".equalsIgnoreCase(searchGroup.getVisibility())
                && !revoked && !active && !canManageSearchGroup(user, searchGroup)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }
        if (SearchGroup.STATUS_CLOSED.equalsIgnoreCase(searchGroup.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This search group is closed");
        }

        if (revoked) {
            return requestVolunteerRejoin(existingVolunteer, searchGroup);
        }
        if (active) {
            log.warn("User {} is already an active volunteer in search group {}", userId, searchGroupId);
            return new JoinSearchGroupResult(existingVolunteer, true, false, false, null, false);
        }

        Volunteer savedVolunteer = addVolunteerMembership(searchGroup, user);
        boolean reactivated = existingVolunteer != null;
        log.info("User {} joined search group {} as volunteer volunteerId={} reactivated={}",
                userId, searchGroupId, savedVolunteer.getId(), reactivated);

        eventPublisher.publishEvent(new VolunteerJoinedGroupEvent(userId, searchGroupId));

        return new JoinSearchGroupResult(savedVolunteer, false, reactivated, false, null, false);
    }

    // REVIEWED
    private JoinSearchGroupResult requestVolunteerRejoin(Volunteer membership, SearchGroup searchGroup) {
        VolunteerRejoinRequest existingRequest = rejoinRequestRepository
                .findFirstByVolunteerIdAndStatusOrderByCreatedAtDesc(membership.getId(), Status.PENDING)
                .orElse(null);
        if (existingRequest != null) {
            return new JoinSearchGroupResult(
                    membership, false, false, true, existingRequest.getId(), true);
        }

        VolunteerRejoinRequest request = rejoinRequestRepository.save(VolunteerRejoinRequest.builder()
                .volunteer(membership)
                .searchGroup(searchGroup)
                .status(Status.PENDING)
                .createdAt(Instant.now())
                .build());
        eventPublisher.publishEvent(new VolunteerRejoinRequestedEvent(request.getId()));
        log.info("Created rejoin request requestId={} userId={} searchGroupId={}",
                request.getId(), membership.getUser().getId(), searchGroup.getId());
        return new JoinSearchGroupResult(membership, false, false, true, request.getId(), false);
    }

    public record JoinSearchGroupResult(
            Volunteer volunteer,
            boolean alreadyMember,
            boolean reactivated,
            boolean pendingApproval,
            Long rejoinRequestId,
            boolean requestAlreadyPending) {
    }

    // REVIEWED
    public boolean canAccessSearchGroup(User user, SearchGroup searchGroup) {
        if (user == null || searchGroup == null) {
            return false;
        }
        if (canManageSearchGroup(user, searchGroup)) {
            return true;
        }
        boolean groupClosed = SearchGroup.STATUS_CLOSED.equalsIgnoreCase(searchGroup.getStatus());
        if (!groupClosed && "PUBLIC".equalsIgnoreCase(searchGroup.getVisibility())) {
            return true;
        }

        Volunteer membership = volunteerRepository.findByUserIdAndSearchGroupId(
                user.getId(), searchGroup.getId());
        return isActiveMember(membership);
    }

    /**
     * Removes a volunteer from a search group
     */
    // REVIEWED
    public void leaveSearchGroup(Long userId, Long searchGroupId) {
        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        if (searchGroup.getCreatedBy() != null
                && userId.equals(searchGroup.getCreatedBy().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The group creator must remain an active member");
        }
        Volunteer volunteer = volunteerRepository
            .findByUserIdAndSearchGroupId(userId, searchGroupId);

        if (volunteer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }
        if (!isActiveMember(volunteer)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are not an active member of this group");
        }

        volunteer.setActive(false);
        volunteer.setRevoked(false);
        volunteerRepository.save(volunteer);

        log.info("User {} left search group {}", userId, searchGroupId);
    }

    /**
     * Creates a new search group for a lost pet and notifies nearby volunteers
     * @deprecated use the command-based overload to avoid positional arguments.
     */
    @Deprecated
    // REVIEWED
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
        return createSearchGroupForLostPet(
                lostPet,
                petOwner,
                new SearchGroupCreationCommand(
                        groupName,
                        "PRIVATE",
                        description,
                        status,
                        area,
                        city,
                        leaderName,
                        leaderPhone,
                        coverageRadiusKm,
                        volunteerUserIds,
                        null,
                        null));
    }

    // REVIEWED
    public SearchGroup createSearchGroupForLostPet(
            Pet lostPet,
            User petOwner,
            SearchGroupCreationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Search group creation command is required");
        }
        String groupName = normalizeGroupName(command.groupName());
        String visibility = command.visibility();
        String description = validateLength(command.description(), "description", 1000);
        String area = validateLength(command.area(), "area", 255);
        String city = validateLength(command.city(), "city", 255);
        String leaderName = validateLength(command.leaderName(), "leaderName", 255);
        String leaderPhone = validateLength(command.leaderPhone(), "leaderPhone", 255);
        Double coverageRadiusKm = command.coverageRadiusKm();
        List<Long> volunteerUserIds = command.volunteerUserIds();
        validateCoverageRadius(coverageRadiusKm);
        if (petOwner.getRole() != User.Role.ADMIN) {
            User creator = userRepository.findByIdForUpdate(petOwner.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            int groupLimit = PlanPolicy.searchGroups(creator);
            if (searchGroupRepository.countByCreatedById(creator.getId()) >= groupLimit) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tu plan permite crear hasta " + groupLimit + " grupos de búsqueda");
            }
        }
        SearchGroup searchGroup = SearchGroup.builder()
            .pet(lostPet)
            .createdBy(petOwner)
            .groupName(groupName)
            .visibility(normalizeVisibility(visibility))
            .description(description)
            .status(SearchGroup.STATUS_ACTIVE)
            .area(area)
            .city(city)
            .leaderName(leaderName != null ? leaderName : petOwner.getName())
            .leaderPhone(leaderPhone)
            .coverageRadiusKm(coverageRadiusKm)
            .whatsappGroupLink(normalizeWhatsappGroupLink(command.whatsappGroupLink()))
            .createdAt(Instant.now())
            .build();
        setImageIfPresent(searchGroup, command.image());

        SearchGroup savedGroup = searchGroupRepository.save(searchGroup);

        // Keep the creator active as a member of the group.
        addVolunteerMembership(savedGroup, petOwner);


        if (volunteerUserIds != null) {
            volunteerUserIds.stream()
                    .filter(id -> id != null && !id.equals(petOwner.getId()))
                    .distinct()
                    .map(id -> userRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Volunteer not found with id: " + id)))
                    .forEach(user -> {
                        addVolunteerMembership(savedGroup, user);
                    });
        }
        log.info("Created search group {} for lost pet {}", savedGroup.getId(), lostPet.getId());

        eventPublisher.publishEvent(new SearchGroupCreatedEvent(
                savedGroup.getId(),
                savedGroup.getGroupName(),
                lostPet.getName(),
                lostPet.getType(),
                savedGroup.getArea(),
                savedGroup.getCity()));

        return savedGroup;
    }

    // REVIEWED
    public SearchGroup updateSearchGroup(Long searchGroupId, User actingUser, SearchGroupUpdateRequest request) {
        return updateSearchGroup(searchGroupId, actingUser, request, null);
    }

    // REVIEWED
    public SearchGroup updateSearchGroup(
            Long searchGroupId,
            User actingUser,
            SearchGroupUpdateRequest request,
            MultipartFile image) {
        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        validateCoverageRadius(request.coverageRadiusKm());

        if (request.groupName() != null) {
            searchGroup.setGroupName(normalizeGroupName(request.groupName()));
        }
        if (request.visibility() != null) {
            searchGroup.setVisibility(normalizeVisibility(request.visibility()));
        }
        if (request.description() != null) {
            searchGroup.setDescription(validateLength(request.description(), "description", 1000));
        }
        if (request.status() != null) {
            String requestedStatus = request.status().trim().toUpperCase(Locale.ROOT);
            if (!SearchGroup.STATUS_ACTIVE.equals(requestedStatus)
                    && !SearchGroup.STATUS_CLOSED.equals(requestedStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or CLOSED");
            }
            if (!isCreator(actingUser, searchGroup)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the group creator can change its status");
            }
            searchGroup.setStatus(requestedStatus);
        }
        if (request.area() != null) {
            searchGroup.setArea(validateLength(request.area(), "area", 255));
        }
        if (request.city() != null) {
            searchGroup.setCity(validateLength(request.city(), "city", 255));
        }
        if (request.leaderName() != null) {
            searchGroup.setLeaderName(validateLength(request.leaderName(), "leaderName", 255));
        }
        if (request.leaderPhone() != null) {
            searchGroup.setLeaderPhone(validateLength(request.leaderPhone(), "leaderPhone", 255));
        }
        if (request.coverageRadiusKm() != null) searchGroup.setCoverageRadiusKm(request.coverageRadiusKm());
        if (request.whatsappGroupLink() != null) {
            searchGroup.setWhatsappGroupLink(normalizeWhatsappGroupLink(request.whatsappGroupLink()));
        }
        setImageIfPresent(searchGroup, image);
        return searchGroupRepository.save(searchGroup);
    }

    // REVIEWED
    public Volunteer addVolunteer(Long searchGroupId, Long userId, User actingUser) {
        log.info("Add group member request actingUserId={} targetUserId={} searchGroupId={}",
                actingUser == null ? null : actingUser.getId(), userId, searchGroupId);

        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        if (SearchGroup.STATUS_CLOSED.equalsIgnoreCase(searchGroup.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This search group is closed");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Volunteer previousMembership = volunteerRepository.findByUserIdAndSearchGroupId(userId, searchGroupId);
        boolean wasAlreadyActive = isActiveMember(previousMembership);
        if (previousMembership != null && Boolean.TRUE.equals(previousMembership.getRevoked())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A revoked member must request to rejoin; only the group creator can decide");
        }

        // Keep an active membership unchanged; otherwise create or reactivate it and notify listeners.
        Volunteer volunteer = wasAlreadyActive
                ? previousMembership
                : addVolunteerMembership(searchGroup, user);

        // Notify listeners only when this call creates or reactivates a membership.
        if (!wasAlreadyActive) {
            eventPublisher.publishEvent(new VolunteerAddedToGroupEvent(userId, searchGroupId));
        }
        log.info("Group member added actingUserId={} targetUserId={} searchGroupId={} volunteerId={} active={}",
                actingUser == null ? null : actingUser.getId(), userId, searchGroupId,
                volunteer.getId(), volunteer.getActive());
        return volunteer;
    }

    // REVIEWED
    public void removeVolunteer(Long searchGroupId, Long userId, User actingUser) {
        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        if (searchGroup.getCreatedBy() != null && userId.equals(searchGroup.getCreatedBy().getId())) {
            throw new RuntimeException("The group creator cannot be removed");
        }
        Volunteer membership = volunteerRepository.findByUserIdAndSearchGroupId(userId, searchGroupId);
        if (membership == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer membership not found");
        }
        membership.setActive(false);
        membership.setRevoked(true);
        volunteerRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<VolunteerCandidateDto> searchVolunteerCandidates(
            Long searchGroupId,
            String query,
            User actingUser,
            Pageable pageable) {
        SearchGroup searchGroup = requireSearchGroup(searchGroupId);
        requireGroupManager(actingUser, searchGroup);
        if (SearchGroup.STATUS_CLOSED.equalsIgnoreCase(searchGroup.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This search group is closed");
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new RuntimeException("q must contain at least 2 characters");
        }
        if (normalizedQuery.length() > 100) {
            throw new RuntimeException("q cannot exceed 100 characters");
        }
        return userRepository.searchVolunteerCandidates(searchGroupId, normalizedQuery, pageable);
    }

    // REVIEWED
    @Transactional(readOnly = true)
    public List<VolunteerRejoinRequestDto> listPendingRejoinRequests(Long searchGroupId, User actingUser) {
        SearchGroup searchGroup = requireSearchGroup(searchGroupId);
        requireGroupCreator(actingUser, searchGroup);
        return rejoinRequestRepository.findBySearchGroupIdAndStatusOrderByCreatedAtAsc(
                        searchGroupId, Status.PENDING).stream()
                .map(this::toRejoinRequestDto)
                .toList();
    }

    // REVIEWED
    public VolunteerRejoinRequestDto approveRejoinRequest(
            Long searchGroupId,
            Long requestId,
            User actingUser) {
        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        requireGroupCreator(actingUser, searchGroup);
        if (SearchGroup.STATUS_CLOSED.equalsIgnoreCase(searchGroup.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This search group is closed");
        }
        VolunteerRejoinRequest request = requirePendingRejoinRequest(searchGroupId, requestId);
        Volunteer membership = request.getVolunteer();
        if (!Boolean.TRUE.equals(membership.getRevoked()) || Boolean.TRUE.equals(membership.getActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This membership is no longer awaiting rejoin approval");
        }

        membership.setActive(true);
        membership.setRevoked(false);
        membership.setJoinedAt(Instant.now());
        volunteerRepository.save(membership);
        request.setStatus(Status.APPROVED);
        request.setResolvedAt(Instant.now());
        request.setReviewedBy(userRepository.findById(actingUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Creator not found")));
        VolunteerRejoinRequest savedRequest = rejoinRequestRepository.save(request);
        eventPublisher.publishEvent(new VolunteerRejoinResolvedEvent(savedRequest.getId()));
        return toRejoinRequestDto(savedRequest);
    }

    // REVIEWED
    public VolunteerRejoinRequestDto rejectRejoinRequest(
            Long searchGroupId,
            Long requestId,
            User actingUser) {
        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        requireGroupCreator(actingUser, searchGroup);
        VolunteerRejoinRequest request = requirePendingRejoinRequest(searchGroupId, requestId);
        request.setStatus(Status.REJECTED);
        request.setResolvedAt(Instant.now());
        request.setReviewedBy(userRepository.findById(actingUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Creator not found")));
        VolunteerRejoinRequest savedRequest = rejoinRequestRepository.save(request);
        eventPublisher.publishEvent(new VolunteerRejoinResolvedEvent(savedRequest.getId()));
        return toRejoinRequestDto(savedRequest);
    }

    // REVIEWED
    private VolunteerRejoinRequest requirePendingRejoinRequest(Long searchGroupId, Long requestId) {
        VolunteerRejoinRequest request = rejoinRequestRepository
                .findByIdAndSearchGroupId(requestId, searchGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rejoin request not found"));
        if (request.getStatus() != Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This rejoin request has already been resolved");
        }
        return request;
    }

    // REVIEWED
    private void requireGroupCreator(User actingUser, SearchGroup searchGroup) {
        if (actingUser == null || searchGroup.getCreatedBy() == null
                || !actingUser.getId().equals(searchGroup.getCreatedBy().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }
    }

    // REVIEWED
    private VolunteerRejoinRequestDto toRejoinRequestDto(VolunteerRejoinRequest request) {
        Volunteer membership = request.getVolunteer();
        User volunteerUser = membership == null ? null : membership.getUser();
        return new VolunteerRejoinRequestDto(
                request.getId(),
                request.getSearchGroup().getId(),
                volunteerUser == null ? null : volunteerUser.getId(),
                volunteerUser == null ? null : volunteerUser.getName(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getResolvedAt());
    }

    // REVIEWED
    private Volunteer addVolunteerMembership(SearchGroup searchGroup, User user) {
        Volunteer volunteer = volunteerRepository.findByUserIdAndSearchGroupId(user.getId(), searchGroup.getId());
        boolean reactivated = volunteer != null;
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
        volunteer.setRevoked(false);
        Volunteer savedVolunteer = volunteerRepository.save(volunteer);
        log.debug("Saved volunteer membership userId={} searchGroupId={} volunteerId={} reactivated={}",
                user.getId(), searchGroup.getId(), savedVolunteer.getId(), reactivated);
        return savedVolunteer;
    }

    private SearchGroup requireSearchGroup(Long searchGroupId) {
        return searchGroupRepository.findById(searchGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found"));
    }

    // REVIEWED
    private SearchGroup requireSearchGroupForUpdate(Long searchGroupId) {
        return searchGroupRepository.findByIdForUpdate(searchGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found"));
    }

    // REVIEWED
    private void requireGroupManager(User actingUser, SearchGroup searchGroup) {
        if (!canManageSearchGroup(actingUser, searchGroup)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }
    }

    private boolean canManageSearchGroup(User user, SearchGroup searchGroup) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == User.Role.ADMIN || isCreator(user, searchGroup)) {
            return true;
        }
        Pet pet = searchGroup.getPet();
        return pet != null && pet.getOwner() != null && user.getId().equals(pet.getOwner().getId());
    }

    private boolean isActiveMember(Volunteer membership) {
        return membership != null && Boolean.TRUE.equals(membership.getActive())
                && !Boolean.TRUE.equals(membership.getRevoked());
    }

    // REVIEWED
    public SearchGroup closeSearchGroup(Long searchGroupId, User actingUser) {
        SearchGroup searchGroup = requireSearchGroupForUpdate(searchGroupId);
        if (!isCreator(actingUser, searchGroup)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }
        searchGroup.setStatus(SearchGroup.STATUS_CLOSED);
        return searchGroupRepository.save(searchGroup);
    }

    // REVIEWED
    private boolean isCreator(User user, SearchGroup searchGroup) {
        return user != null && searchGroup.getCreatedBy() != null
                && user.getId().equals(searchGroup.getCreatedBy().getId());
    }

    private void validateCoverageRadius(Double coverageRadiusKm) {
        if (coverageRadiusKm != null && coverageRadiusKm < 0) {
            throw new RuntimeException("coverageRadiusKm cannot be negative");
        }
    }

    private String normalizeGroupName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupName is required");
        }
        return validateLength(name.trim(), "groupName", 255);
    }

    private String validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.codePointCount(0, value.length()) > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " cannot exceed " + maxLength + " characters");
        }
        return value;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "PRIVATE";
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        if (!"PRIVATE".equals(normalized) && !"PUBLIC".equals(normalized)) {
            throw new IllegalArgumentException("visibility must be PRIVATE or PUBLIC");
        }
        return normalized;
    }

    private void setImageIfPresent(SearchGroup searchGroup, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Group image must be JPEG, PNG, WebP, or GIF");
        }
        imageUploadLimits.validateImageSize(image.getSize(), "Group image");
        try {
            searchGroup.setImageContentType(contentType);
            searchGroup.setImageFileName(validateLength(image.getOriginalFilename(), "image file name", 255));
            searchGroup.setImageData(image.getBytes());
        } catch (IOException exception) {
            throw new RuntimeException("Could not read group image", exception);
        }
    }

    private String normalizeWhatsappGroupLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String normalized = validateLength(link.trim(), "whatsappGroupLink", 1000);
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !("chat.whatsapp.com".equals(host)
                    || "www.whatsapp.com".equals(host)
                    || "wa.me".equals(host))) {
                throw new IllegalArgumentException(
                        "whatsappGroupLink must be an HTTPS WhatsApp invitation link");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("whatsappGroupLink must")) {
                throw exception;
            }
            throw new IllegalArgumentException("whatsappGroupLink must be a valid HTTPS WhatsApp invitation link");
        }
    }

    @Transactional(readOnly = true)
    public SearchGroupImage findImageById(Long searchGroupId) {
        SearchGroup searchGroup = searchGroupRepository.findById(searchGroupId).orElse(null);
        if (searchGroup == null || searchGroup.getImageData() == null
                || searchGroup.getImageContentType() == null) {
            return null;
        }
        Pet pet = searchGroup.getPet();
        return new SearchGroupImage(
                pet == null || pet.getOwner() == null ? null : pet.getOwner().getId(),
                pet == null ? null : pet.getStatus(),
                searchGroup.getImageData(),
                searchGroup.getImageContentType(),
                searchGroup.getImageFileName());
    }

    public record SearchGroupImage(
            Long petOwnerId,
            Pet.Status petStatus,
            byte[] data,
            String contentType,
            String fileName) {
        public String fileNameOrDefault() {
            return fileName == null || fileName.isBlank() ? "search-group-image" : fileName;
        }
    }

    /**
     * Notifies all active volunteers in a search group about pet status updates
     */
    // REVIEWED
    @Async("notificationExecutor")
    public void notifyVolunteersInGroup(SearchGroup searchGroup, String updateMessage) {
        if (searchGroup == null) {
            return;
        }
        List<Volunteer> activeVolunteers = volunteerRepository
                .findBySearchGroupIdAndActiveTrueOrderByJoinedAtAsc(searchGroup.getId());
        if (activeVolunteers.isEmpty()) {
            log.debug("No volunteers to notify for search group {}", searchGroup != null ? searchGroup.getId() : "null");
            return;
        }

        for (Volunteer membership : activeVolunteers) {
            User volunteer = membership.getUser();
            if (volunteer.getPhone() != null && !volunteer.getPhone().isBlank()) {
                notificationService.sendPhoneNotification(volunteer.getPhone(), updateMessage);
            }
            if (volunteer.getEmail() != null && !volunteer.getEmail().isBlank()) {
                String subject = "Update: " + (searchGroup.getPet() != null ? searchGroup.getPet().getName() : "Search") + " Search";
                notificationService.sendEmail(volunteer.getEmail(), subject, updateMessage);
            }
        }

        log.info("Notified {} volunteers in search group {}", activeVolunteers.size(), searchGroup.getId());
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
       return volunteerRepository.findBySearchGroup_PetIdAndActiveTrue(id);
    }
}
