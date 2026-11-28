package com.example.pettracker.controller;

import com.example.pettracker.dto.SearchGroupCreateRequest;
import com.example.pettracker.dto.SearchGroupCreationCommand;
import com.example.pettracker.dto.SearchGroupDto;
import com.example.pettracker.dto.SearchGroupRejoinOptionDto;
import com.example.pettracker.dto.SearchGroupUpdateRequest;
import com.example.pettracker.dto.JoinSearchGroupResponse;
import com.example.pettracker.dto.VolunteerCandidateDto;
import com.example.pettracker.dto.VolunteerDto;
import com.example.pettracker.dto.VolunteerRejoinRequestDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.VolunteerRejoinRequest.Status;
import com.example.pettracker.mapper.SearchGroupMapper;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.VolunteerRejoinRequestRepository;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.CurrentUserService;
import com.example.pettracker.service.VolunteerService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/search-groups")
public class SearchGroupController {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CANDIDATE_PAGE_SIZE = 20;

    private final VolunteerService volunteerService;
    private final CurrentUserService currentUserService;
    private final PetService petService;
    private final SearchGroupRepository searchGroupRepository;
    private final VolunteerRejoinRequestRepository rejoinRequestRepository;
    private final SearchGroupMapper searchGroupMapper;

    public SearchGroupController(
            VolunteerService volunteerService,
            CurrentUserService currentUserService,
            PetService petService,
            SearchGroupRepository searchGroupRepository,
            VolunteerRejoinRequestRepository rejoinRequestRepository,
            SearchGroupMapper searchGroupMapper) {
        this.volunteerService = volunteerService;
        this.currentUserService = currentUserService;
        this.petService = petService;
        this.searchGroupRepository = searchGroupRepository;
        this.rejoinRequestRepository = rejoinRequestRepository;
        this.searchGroupMapper = searchGroupMapper;
    }

    // REVIEWED
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<SearchGroupDto> create(@RequestBody SearchGroupCreateRequest request, Authentication authentication) {
        return createSearchGroup(request, null, authentication);
    }

    // REVIEWED
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<SearchGroupDto> createWithImage(
            @RequestPart("group") SearchGroupCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        return createSearchGroup(request, image, authentication);
    }

    // REVIEWED
    private ResponseEntity<SearchGroupDto> createSearchGroup(
            SearchGroupCreateRequest request,
            MultipartFile image,
            Authentication authentication) {
        if (request == null || request.petId() == null) {
            throw new RuntimeException("petId is required");
        }
        User user = currentUserService.require(authentication);
        Pet pet = petService.findById(request.petId());
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getRole() != User.Role.ADMIN
                && (pet.getOwner() == null || !pet.getOwner().getId().equals(user.getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SearchGroup saved = volunteerService.createSearchGroupForLostPet(
                        pet,
                        user,
                        new SearchGroupCreationCommand(
                                request.groupName(),
                                request.visibility(),
                                request.description(),
                                request.status(),
                                request.area(),
                                request.city(),
                                request.leaderName(),
                                request.leaderPhone(),
                                request.coverageRadiusKm(),
                                user.getRole() == User.Role.ADMIN ? request.volunteerUserIds() : List.of(),
                                request.whatsappGroupLink(),
                                image));
        return ResponseEntity.status(HttpStatus.CREATED).body(searchGroupMapper.toDto(saved, user));
    }

    // REVIEWED
    @PutMapping(path = "/{searchGroupId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public SearchGroupDto update(
            @PathVariable Long searchGroupId,
            @RequestBody SearchGroupUpdateRequest request,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        SearchGroup updated = volunteerService.updateSearchGroup(searchGroupId, user, request);
        return searchGroupMapper.toDto(updated, user);
    }

    // REVIEWED
    @PutMapping(path = "/{searchGroupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public SearchGroupDto updateWithImage(
            @PathVariable Long searchGroupId,
            @RequestPart("group") SearchGroupUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        SearchGroup updated = volunteerService.updateSearchGroup(searchGroupId, user, request, image);
        return searchGroupMapper.toDto(updated, user);
    }

    // REVIEWED
    @PostMapping("/{searchGroupId}/close")
    @Transactional
    public SearchGroupDto close(
            @PathVariable Long searchGroupId,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        SearchGroup closed = volunteerService.closeSearchGroup(searchGroupId, user);
        return searchGroupMapper.toDto(closed, user);
    }

    @GetMapping("/{searchGroupId}")
    @Transactional(readOnly = true)
    public ResponseEntity<SearchGroupDto> getById(
            @PathVariable Long searchGroupId,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        SearchGroup group = searchGroupRepository.findById(searchGroupId).orElse(null);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }

        if (!volunteerService.canAccessSearchGroup(user, group)) {
            // Do not reveal whether a private group exists to an outsider.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }

        return ResponseEntity.ok(searchGroupMapper.toDto(group, user));
    }

    @GetMapping("/{searchGroupId}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getImage(
            @PathVariable Long searchGroupId,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        SearchGroup group = searchGroupRepository.findById(searchGroupId).orElse(null);
        if (group == null || !volunteerService.canAccessSearchGroup(user, group)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group image not found");
        }
        VolunteerService.SearchGroupImage image = volunteerService.findImageById(searchGroupId);
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group image not found");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.fileNameOrDefault() + "\"")
                .body(image.data());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SearchGroupDto> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pageable pageable = pageRequest(page, size);
        List<SearchGroup> groups = user.getRole() == User.Role.ADMIN
                ? searchGroupRepository.findAll(pageable).getContent()
                : searchGroupRepository.findDiscoverableForUser(user.getId(), pageable);
        return searchGroupMapper.toDtos(groups, user);
    }

    @GetMapping("/pet/{petId}")
    @Transactional(readOnly = true)
    public List<SearchGroupDto> listForPet(
            @PathVariable Long petId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pet pet = petService.findById(petId);
        if (pet == null) {
            return List.of();
        }
        boolean owner = pet.getOwner() != null && user.getId().equals(pet.getOwner().getId());
        Pageable pageable = pageRequest(page, size);
        List<SearchGroup> groups = user.getRole() == User.Role.ADMIN || owner
                ? searchGroupRepository.findByPetId(petId, pageable)
                : searchGroupRepository.findByPetIdAndActiveVolunteerUserId(petId, user.getId(), pageable);
        return searchGroupMapper.toDtos(groups, user);
    }

    // REVIEWED
    @PostMapping("/{searchGroupId}/join")
    public ResponseEntity<JoinSearchGroupResponse> join(
            @PathVariable Long searchGroupId,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        VolunteerService.JoinSearchGroupResult result = volunteerService.joinSearchGroupWithResult(
                user.getId(), searchGroupId);
        String message = result.pendingApproval()
                ? result.requestAlreadyPending()
                        ? "Tu solicitud de reingreso ya está pendiente de revisión."
                        : "Tu solicitud de reingreso fue enviada al creador del grupo."
                : result.alreadyMember()
                        ? "Ya perteneces a este grupo de búsqueda."
                        : result.reactivated()
                                ? "Te has unido nuevamente al grupo de búsqueda."
                                : "Te has unido al grupo de búsqueda correctamente.";
        JoinSearchGroupResponse response = new JoinSearchGroupResponse(
                searchGroupMapper.toDto(result.volunteer()),
                message,
                result.alreadyMember(),
                result.reactivated(),
                result.pendingApproval(),
                result.rejoinRequestId(),
                result.requestAlreadyPending());
        HttpStatus status = result.pendingApproval()
                ? HttpStatus.ACCEPTED
                : result.alreadyMember() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(response);
    }

    // REVIEWED
    @PostMapping("/{searchGroupId}/volunteers/{userId}")
    public ResponseEntity<VolunteerDto> addVolunteer(
            @PathVariable Long searchGroupId,
            @PathVariable Long userId,
            Authentication authentication) {
        VolunteerDto volunteer = searchGroupMapper.toDto(
                volunteerService.addVolunteer(searchGroupId, userId, currentUserService.require(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(volunteer);
    }

    // REVIEWED
    @GetMapping("/{searchGroupId}/rejoin-requests")
    public List<VolunteerRejoinRequestDto> pendingRejoinRequests(
            @PathVariable Long searchGroupId,
            Authentication authentication) {
        return volunteerService.listPendingRejoinRequests(
                searchGroupId, currentUserService.require(authentication));
    }

    // REVIEWED
    @PostMapping("/{searchGroupId}/rejoin-requests/{requestId}/approve")
    public VolunteerRejoinRequestDto approveRejoinRequest(
            @PathVariable Long searchGroupId,
            @PathVariable Long requestId,
            Authentication authentication) {
        return volunteerService.approveRejoinRequest(
                searchGroupId, requestId, currentUserService.require(authentication));
    }

    // REVIEWED
    @PostMapping("/{searchGroupId}/rejoin-requests/{requestId}/reject")
    public VolunteerRejoinRequestDto rejectRejoinRequest(
            @PathVariable Long searchGroupId,
            @PathVariable Long requestId,
            Authentication authentication) {
        return volunteerService.rejectRejoinRequest(
                searchGroupId, requestId, currentUserService.require(authentication));
    }

    // REVIEWED
    @DeleteMapping("/{searchGroupId}/volunteers/{userId}")
    public ResponseEntity<Void> removeVolunteer(
            @PathVariable Long searchGroupId,
            @PathVariable Long userId,
            Authentication authentication) {
        volunteerService.removeVolunteer(searchGroupId, userId, currentUserService.require(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{searchGroupId}/member-candidates")
    public List<VolunteerCandidateDto> memberCandidates(
            @PathVariable Long searchGroupId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        if (user.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can search users");
        }
        return volunteerService.searchVolunteerCandidates(
                searchGroupId,
                q,
                user,
                candidatePageRequest(page, size));
    }

    // REVIEWED
    @PostMapping("/{searchGroupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUserService.require(authentication);
        volunteerService.leaveSearchGroup(user.getId(), searchGroupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{searchGroupId}/volunteers")
    public List<VolunteerDto> volunteers(
            @PathVariable Long searchGroupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        SearchGroup group = searchGroupRepository.findById(searchGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found"));
        if (!volunteerService.canAccessSearchGroup(user, group)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found");
        }
        boolean owner = group.getPet() != null && group.getPet().getOwner() != null
                && user.getId().equals(group.getPet().getOwner().getId());
        if (user.getRole() != User.Role.ADMIN && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the group owner or an admin can view members");
        }
        return volunteerService.getActiveVolunteersForGroup(searchGroupId, pageRequest(page, size)).stream()
                .map(searchGroupMapper::toDto)
                .toList();
    }

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public List<SearchGroupDto> myGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        return searchGroupMapper.toDtos(volunteerService.getUserSearchGroups(user.getId(), pageRequest(page, size)), user);
    }

    @GetMapping("/my/rejoin-options")
    @Transactional(readOnly = true)
    public List<SearchGroupRejoinOptionDto> myRejoinOptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        return rejoinRequestRepository.findRejoinOptions(
                user.getId(), Status.PENDING, pageRequest(page, size).withSort(Sort.unsorted()));
    }

    private PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
    }

    private Pageable candidatePageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_CANDIDATE_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by("name").ascending().and(Sort.by("email").ascending()));
    }

}
