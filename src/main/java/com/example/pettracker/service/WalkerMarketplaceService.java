package com.example.pettracker.service;

import com.example.pettracker.dto.WalkerMarketplaceDtos.AdminWalkerUpsertRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.CurrentUserResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.DogWalkResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkDecisionRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkMessageRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkMessageResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkPositionRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkPositionResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkQuoteRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkRequestCreateRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkRequestResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkerApplicationRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkerCardResponse;
import com.example.pettracker.entity.DogWalk;
import com.example.pettracker.entity.DogWalkPosition;
import com.example.pettracker.entity.DogWalkerProfile;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.WalkMessage;
import com.example.pettracker.entity.WalkRequest;
import com.example.pettracker.repository.DogWalkPositionRepository;
import com.example.pettracker.repository.DogWalkRepository;
import com.example.pettracker.repository.DogWalkerProfileRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.repository.WalkMessageRepository;
import com.example.pettracker.repository.WalkRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class WalkerMarketplaceService {

    private final DogWalkerProfileRepository dogWalkerProfileRepository;
    private final WalkRequestRepository walkRequestRepository;
    private final WalkMessageRepository walkMessageRepository;
    private final DogWalkRepository dogWalkRepository;
    private final DogWalkPositionRepository dogWalkPositionRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    public WalkerMarketplaceService(
            DogWalkerProfileRepository dogWalkerProfileRepository,
            WalkRequestRepository walkRequestRepository,
            WalkMessageRepository walkMessageRepository,
            DogWalkRepository dogWalkRepository,
            DogWalkPositionRepository dogWalkPositionRepository,
            UserRepository userRepository,
            PetRepository petRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder) {
        this.dogWalkerProfileRepository = dogWalkerProfileRepository;
        this.walkRequestRepository = walkRequestRepository;
        this.walkMessageRepository = walkMessageRepository;
        this.dogWalkRepository = dogWalkRepository;
        this.dogWalkPositionRepository = dogWalkPositionRepository;
        this.userRepository = userRepository;
        this.petRepository = petRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<WalkerCardResponse> listPublicWalkers() {
        return dogWalkerProfileRepository
                .findByApprovalStatusAndActiveTrueOrderByCreatedAtDesc(DogWalkerProfile.ApprovalStatus.APPROVED)
                .stream()
                .map(this::toWalkerCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalkerCardResponse> listAllWalkers() {
        return dogWalkerProfileRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toWalkerCard)
                .toList();
    }

    public WalkerCardResponse createWalkerApplication(WalkerApplicationRequest request) {
        validateNewUser(request.getEmail(), request.getPassword());

        User user = userRepository.save(User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(User.Role.WALKER)
                .build());

        DogWalkerProfile profile = dogWalkerProfileRepository.save(DogWalkerProfile.builder()
                .user(user)
                .bio(request.getBio())
                .neighborhood(request.getNeighborhood())
                .experienceYears(request.getExperienceYears())
                .basePrice(request.getBasePrice())
                .priceNotes(request.getPriceNotes())
                .services(request.getServices())
                .availability(request.getAvailability())
                .active(Boolean.TRUE)
                .approvalStatus(DogWalkerProfile.ApprovalStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        return toWalkerCard(profile);
    }

    public WalkerCardResponse createWalkerByAdmin(AdminWalkerUpsertRequest request) {
        validateNewUser(request.getEmail(), request.getPassword());

        User user = userRepository.save(User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(User.Role.WALKER)
                .build());

        DogWalkerProfile profile = dogWalkerProfileRepository.save(DogWalkerProfile.builder()
                .user(user)
                .bio(request.getBio())
                .neighborhood(request.getNeighborhood())
                .experienceYears(request.getExperienceYears())
                .basePrice(request.getBasePrice())
                .priceNotes(request.getPriceNotes())
                .services(request.getServices())
                .availability(request.getAvailability())
                .active(request.getActive() == null ? Boolean.TRUE : request.getActive())
                .approvalStatus(request.getApprovalStatus() == null
                        ? DogWalkerProfile.ApprovalStatus.APPROVED
                        : request.getApprovalStatus())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        return toWalkerCard(profile);
    }

    public WalkerCardResponse updateWalkerByAdmin(Long profileId, AdminWalkerUpsertRequest request) {
        DogWalkerProfile profile = findWalkerProfile(profileId);
        User user = profile.getUser();

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
                throw new RuntimeException("User already exists with email: " + request.getEmail());
            });
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        user.setRole(User.Role.WALKER);
        userRepository.save(user);

        profile.setBio(request.getBio());
        profile.setNeighborhood(request.getNeighborhood());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setBasePrice(request.getBasePrice());
        profile.setPriceNotes(request.getPriceNotes());
        profile.setServices(request.getServices());
        profile.setAvailability(request.getAvailability());
        profile.setActive(request.getActive() == null ? profile.getActive() : request.getActive());
        profile.setApprovalStatus(request.getApprovalStatus() == null ? profile.getApprovalStatus() : request.getApprovalStatus());
        profile.setUpdatedAt(Instant.now());

        return toWalkerCard(dogWalkerProfileRepository.save(profile));
    }

    public void deleteWalkerByAdmin(Long profileId) {
        DogWalkerProfile profile = findWalkerProfile(profileId);
        dogWalkerProfileRepository.delete(profile);
        userRepository.delete(profile.getUser());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Authentication authentication) {
        User user = currentUserService.require(authentication);
        Long walkerProfileId = dogWalkerProfileRepository.findByUserId(user.getId())
                .map(DogWalkerProfile::getId)
                .orElse(null);
        return new CurrentUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                walkerProfileId
        );
    }

    @Transactional(readOnly = true)
    public WalkerCardResponse getMyWalkerProfile(Authentication authentication) {
        User current = currentUserService.require(authentication);
        return dogWalkerProfileRepository.findByUserId(current.getId())
                .map(this::toWalkerCard)
                .orElse(null);
    }

    public WalkRequestResponse createWalkRequest(Authentication authentication, WalkRequestCreateRequest request) {
        User owner = currentUserService.require(authentication);
        DogWalkerProfile walkerProfile = findWalkerProfile(request.getWalkerProfileId());
        if (!Boolean.TRUE.equals(walkerProfile.getActive())
                || walkerProfile.getApprovalStatus() != DogWalkerProfile.ApprovalStatus.APPROVED) {
            throw new RuntimeException("Walker is not available for requests");
        }

        Pet pet = null;
        if (request.getPetId() != null) {
            pet = petRepository.findById(request.getPetId())
                    .orElseThrow(() -> new RuntimeException("Pet not found"));
            if (pet.getOwner() == null || !pet.getOwner().getId().equals(owner.getId())) {
                throw new RuntimeException("Pet does not belong to current user");
            }
        }

        WalkRequest walkRequest = walkRequestRepository.save(WalkRequest.builder()
                .owner(owner)
                .walkerProfile(walkerProfile)
                .pet(pet)
                .requestedStart(request.getRequestedStart())
                .durationMinutes(request.getDurationMinutes())
                .ownerBudget(request.getOwnerBudget())
                .serviceAddress(request.getServiceAddress())
                .notes(request.getNotes())
                .status(WalkRequest.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            walkMessageRepository.save(WalkMessage.builder()
                    .walkRequest(walkRequest)
                    .sender(owner)
                    .body(request.getNotes())
                    .createdAt(Instant.now())
                    .build());
        }

        return toWalkRequestResponse(walkRequest);
    }

    @Transactional(readOnly = true)
    public List<WalkRequestResponse> listMyWalkRequests(Authentication authentication) {
        User current = currentUserService.require(authentication);
        if (current.getRole() == User.Role.WALKER) {
            return walkRequestRepository.findByWalkerProfileUserIdOrderByCreatedAtDesc(current.getId()).stream()
                    .map(this::toWalkRequestResponse)
                    .toList();
        }
        return walkRequestRepository.findByOwnerIdOrderByCreatedAtDesc(current.getId()).stream()
                .map(this::toWalkRequestResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalkRequestResponse getWalkRequest(Authentication authentication, Long walkRequestId) {
        User current = currentUserService.require(authentication);
        WalkRequest walkRequest = findWalkRequest(walkRequestId);
        assertRequestAccess(current, walkRequest);
        return toWalkRequestResponse(walkRequest);
    }

    public WalkRequestResponse quoteWalkRequest(Authentication authentication, Long walkRequestId, WalkQuoteRequest request) {
        User current = currentUserService.require(authentication);
        WalkRequest walkRequest = findWalkRequest(walkRequestId);
        assertWalkerOwnsRequest(current, walkRequest);

        walkRequest.setWalkerQuotedPrice(request.getWalkerQuotedPrice());
        walkRequest.setStatus(request.getStatus() == null ? WalkRequest.Status.NEGOTIATING : request.getStatus());
        walkRequest.setUpdatedAt(Instant.now());
        return toWalkRequestResponse(walkRequestRepository.save(walkRequest));
    }

    public WalkRequestResponse decideWalkRequest(Authentication authentication, Long walkRequestId, WalkDecisionRequest request) {
        User current = currentUserService.require(authentication);
        WalkRequest walkRequest = findWalkRequest(walkRequestId);
        assertOwnerOwnsRequest(current, walkRequest);

        WalkRequest.Status status = request.getStatus();
        if (status == null || (status != WalkRequest.Status.ACCEPTED
                && status != WalkRequest.Status.REJECTED
                && status != WalkRequest.Status.CANCELLED)) {
            throw new RuntimeException("Invalid owner decision");
        }

        walkRequest.setStatus(status);
        walkRequest.setUpdatedAt(Instant.now());
        return toWalkRequestResponse(walkRequestRepository.save(walkRequest));
    }

    public WalkMessageResponse addMessage(Authentication authentication, Long walkRequestId, WalkMessageRequest request) {
        User current = currentUserService.require(authentication);
        WalkRequest walkRequest = findWalkRequest(walkRequestId);
        assertRequestAccess(current, walkRequest);

        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new RuntimeException("Message body is required");
        }

        WalkMessage message = walkMessageRepository.save(WalkMessage.builder()
                .walkRequest(walkRequest)
                .sender(current)
                .body(request.getBody())
                .createdAt(Instant.now())
                .build());
        walkRequest.setUpdatedAt(Instant.now());
        walkRequestRepository.save(walkRequest);
        return toWalkMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public List<WalkMessageResponse> listMessages(Authentication authentication, Long walkRequestId) {
        User current = currentUserService.require(authentication);
        WalkRequest walkRequest = findWalkRequest(walkRequestId);
        assertRequestAccess(current, walkRequest);
        return walkMessageRepository.findByWalkRequestIdOrderByCreatedAtAsc(walkRequestId).stream()
                .map(this::toWalkMessageResponse)
                .toList();
    }

    public DogWalkResponse startWalk(Authentication authentication, Long walkRequestId) {
        User current = currentUserService.require(authentication);
        WalkRequest walkRequest = findWalkRequest(walkRequestId);
        assertWalkerOwnsRequest(current, walkRequest);
        if (walkRequest.getStatus() != WalkRequest.Status.ACCEPTED
                && walkRequest.getStatus() != WalkRequest.Status.NEGOTIATING) {
            throw new RuntimeException("Walk request must be accepted or negotiating before starting");
        }

        Optional<DogWalk> existing = dogWalkRepository.findByWalkRequestId(walkRequestId);
        DogWalk dogWalk = existing.orElseGet(() -> DogWalk.builder()
                .walkRequest(walkRequest)
                .owner(walkRequest.getOwner())
                .walkerProfile(walkRequest.getWalkerProfile())
                .pet(walkRequest.getPet())
                .agreedPrice(walkRequest.getWalkerQuotedPrice() != null
                        ? walkRequest.getWalkerQuotedPrice()
                        : walkRequest.getOwnerBudget())
                .startedAt(Instant.now())
                .status(DogWalk.Status.IN_PROGRESS)
                .build());

        if (dogWalk.getStartedAt() == null) {
            dogWalk.setStartedAt(Instant.now());
        }
        dogWalk.setStatus(DogWalk.Status.IN_PROGRESS);
        DogWalk saved = dogWalkRepository.save(dogWalk);

        walkRequest.setStatus(WalkRequest.Status.IN_PROGRESS);
        walkRequest.setUpdatedAt(Instant.now());
        walkRequestRepository.save(walkRequest);
        return toDogWalkResponse(saved);
    }

    public DogWalkResponse completeWalk(Authentication authentication, Long dogWalkId) {
        User current = currentUserService.require(authentication);
        DogWalk dogWalk = findDogWalk(dogWalkId);
        assertDogWalkAccess(current, dogWalk);

        if (!current.getId().equals(dogWalk.getWalkerProfile().getUser().getId())
                && current.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Only the assigned walker can complete the walk");
        }

        dogWalk.setStatus(DogWalk.Status.COMPLETED);
        dogWalk.setCompletedAt(Instant.now());
        DogWalk saved = dogWalkRepository.save(dogWalk);

        WalkRequest walkRequest = dogWalk.getWalkRequest();
        walkRequest.setStatus(WalkRequest.Status.COMPLETED);
        walkRequest.setUpdatedAt(Instant.now());
        walkRequestRepository.save(walkRequest);

        return toDogWalkResponse(saved);
    }

    public WalkPositionResponse addWalkPosition(Authentication authentication, Long dogWalkId, WalkPositionRequest request) {
        User current = currentUserService.require(authentication);
        DogWalk dogWalk = findDogWalk(dogWalkId);
        assertDogWalkAccess(current, dogWalk);

        if (!current.getId().equals(dogWalk.getWalkerProfile().getUser().getId())
                && current.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Only the assigned walker can send location updates");
        }
        if (dogWalk.getStatus() != DogWalk.Status.IN_PROGRESS) {
            throw new RuntimeException("Walk is not in progress");
        }

        DogWalkPosition position = dogWalkPositionRepository.save(DogWalkPosition.builder()
                .dogWalk(dogWalk)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .recordedAt(Instant.now())
                .build());
        return toWalkPositionResponse(position);
    }

    @Transactional(readOnly = true)
    public DogWalkResponse getDogWalk(Authentication authentication, Long dogWalkId) {
        User current = currentUserService.require(authentication);
        DogWalk dogWalk = findDogWalk(dogWalkId);
        assertDogWalkAccess(current, dogWalk);
        return toDogWalkResponse(dogWalk);
    }

    @Transactional(readOnly = true)
    public List<DogWalkResponse> listMyDogWalks(Authentication authentication) {
        User current = currentUserService.require(authentication);
        if (current.getRole() == User.Role.WALKER) {
            return dogWalkRepository.findByWalkerProfileUserIdOrderByStartedAtDesc(current.getId()).stream()
                    .map(this::toDogWalkResponse)
                    .toList();
        }
        return dogWalkRepository.findByOwnerIdOrderByStartedAtDesc(current.getId()).stream()
                .map(this::toDogWalkResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalkPositionResponse> listWalkPositions(Authentication authentication, Long dogWalkId) {
        User current = currentUserService.require(authentication);
        DogWalk dogWalk = findDogWalk(dogWalkId);
        assertDogWalkAccess(current, dogWalk);
        return dogWalkPositionRepository.findByDogWalkIdOrderByRecordedAtAsc(dogWalkId).stream()
                .map(this::toWalkPositionResponse)
                .toList();
    }

    private void validateNewUser(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (password == null || password.isBlank()) {
            throw new RuntimeException("Password is required");
        }
        userRepository.findByEmail(email).ifPresent(existing -> {
            throw new RuntimeException("User already exists with email: " + email);
        });
    }

    private DogWalkerProfile findWalkerProfile(Long walkerProfileId) {
        return dogWalkerProfileRepository.findById(walkerProfileId)
                .orElseThrow(() -> new RuntimeException("Walker profile not found"));
    }

    private WalkRequest findWalkRequest(Long walkRequestId) {
        return walkRequestRepository.findById(walkRequestId)
                .orElseThrow(() -> new RuntimeException("Walk request not found"));
    }

    private DogWalk findDogWalk(Long dogWalkId) {
        return dogWalkRepository.findById(dogWalkId)
                .orElseThrow(() -> new RuntimeException("Dog walk not found"));
    }

    private void assertRequestAccess(User current, WalkRequest walkRequest) {
        if (current.getRole() == User.Role.ADMIN) {
            return;
        }
        Long currentId = current.getId();
        if (walkRequest.getOwner() != null && currentId.equals(walkRequest.getOwner().getId())) {
            return;
        }
        if (walkRequest.getWalkerProfile() != null
                && walkRequest.getWalkerProfile().getUser() != null
                && currentId.equals(walkRequest.getWalkerProfile().getUser().getId())) {
            return;
        }
        throw new RuntimeException("You do not have access to this walk request");
    }

    private void assertDogWalkAccess(User current, DogWalk dogWalk) {
        if (current.getRole() == User.Role.ADMIN) {
            return;
        }
        Long currentId = current.getId();
        if (dogWalk.getOwner() != null && currentId.equals(dogWalk.getOwner().getId())) {
            return;
        }
        if (dogWalk.getWalkerProfile() != null
                && dogWalk.getWalkerProfile().getUser() != null
                && currentId.equals(dogWalk.getWalkerProfile().getUser().getId())) {
            return;
        }
        throw new RuntimeException("You do not have access to this dog walk");
    }

    private void assertWalkerOwnsRequest(User current, WalkRequest walkRequest) {
        if (current.getRole() == User.Role.ADMIN) {
            return;
        }
        if (walkRequest.getWalkerProfile() == null
                || walkRequest.getWalkerProfile().getUser() == null
                || !current.getId().equals(walkRequest.getWalkerProfile().getUser().getId())) {
            throw new RuntimeException("Current user is not the assigned walker");
        }
    }

    private void assertOwnerOwnsRequest(User current, WalkRequest walkRequest) {
        if (current.getRole() == User.Role.ADMIN) {
            return;
        }
        if (walkRequest.getOwner() == null || !current.getId().equals(walkRequest.getOwner().getId())) {
            throw new RuntimeException("Current user is not the owner of this request");
        }
    }

    private WalkerCardResponse toWalkerCard(DogWalkerProfile profile) {
        User user = profile.getUser();
        return new WalkerCardResponse(
                profile.getId(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                profile.getBio(),
                profile.getNeighborhood(),
                profile.getExperienceYears(),
                profile.getBasePrice(),
                profile.getPriceNotes(),
                profile.getServices(),
                profile.getAvailability(),
                profile.getActive(),
                profile.getApprovalStatus()
        );
    }

    private WalkRequestResponse toWalkRequestResponse(WalkRequest walkRequest) {
        Long activeDogWalkId = dogWalkRepository.findByWalkRequestId(walkRequest.getId())
                .map(DogWalk::getId)
                .orElse(null);
        return new WalkRequestResponse(
                walkRequest.getId(),
                walkRequest.getWalkerProfile().getId(),
                walkRequest.getWalkerProfile().getUser().getName(),
                walkRequest.getOwner().getId(),
                walkRequest.getOwner().getName(),
                walkRequest.getPet() == null ? null : walkRequest.getPet().getId(),
                walkRequest.getPet() == null ? null : walkRequest.getPet().getName(),
                walkRequest.getRequestedStart(),
                walkRequest.getDurationMinutes(),
                walkRequest.getOwnerBudget(),
                walkRequest.getWalkerQuotedPrice(),
                walkRequest.getServiceAddress(),
                walkRequest.getNotes(),
                walkRequest.getStatus(),
                walkRequest.getCreatedAt(),
                walkRequest.getUpdatedAt(),
                activeDogWalkId
        );
    }

    private WalkMessageResponse toWalkMessageResponse(WalkMessage message) {
        return new WalkMessageResponse(
                message.getId(),
                message.getWalkRequest().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getBody(),
                message.getCreatedAt()
        );
    }

    private DogWalkResponse toDogWalkResponse(DogWalk dogWalk) {
        return new DogWalkResponse(
                dogWalk.getId(),
                dogWalk.getWalkRequest().getId(),
                dogWalk.getWalkerProfile().getId(),
                dogWalk.getWalkerProfile().getUser().getName(),
                dogWalk.getOwner().getId(),
                dogWalk.getOwner().getName(),
                dogWalk.getPet() == null ? null : dogWalk.getPet().getId(),
                dogWalk.getPet() == null ? null : dogWalk.getPet().getName(),
                dogWalk.getAgreedPrice(),
                dogWalk.getStartedAt(),
                dogWalk.getCompletedAt(),
                dogWalk.getStatus(),
                dogWalkPositionRepository.findTopByDogWalkIdOrderByRecordedAtDesc(dogWalk.getId())
                        .map(this::toWalkPositionResponse)
                        .orElse(null)
        );
    }

    private WalkPositionResponse toWalkPositionResponse(DogWalkPosition position) {
        return new WalkPositionResponse(
                position.getId(),
                position.getLatitude(),
                position.getLongitude(),
                position.getRecordedAt()
        );
    }
}
