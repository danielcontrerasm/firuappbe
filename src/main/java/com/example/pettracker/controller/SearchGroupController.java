package com.example.pettracker.controller;

import com.example.pettracker.dto.SearchGroupCreateRequest;
import com.example.pettracker.dto.SearchGroupDto;
import com.example.pettracker.dto.VolunteerDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.SearchGroupMapper;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.UserService;
import com.example.pettracker.service.VolunteerService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-groups")
public class SearchGroupController {

    private final VolunteerService volunteerService;
    private final UserService userService;
    private final PetService petService;
    private final SearchGroupRepository searchGroupRepository;

    public SearchGroupController(
            VolunteerService volunteerService,
            UserService userService,
            PetService petService,
            SearchGroupRepository searchGroupRepository) {
        this.volunteerService = volunteerService;
        this.userService = userService;
        this.petService = petService;
        this.searchGroupRepository = searchGroupRepository;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchGroupDto> create(@RequestBody SearchGroupCreateRequest request, Authentication authentication) {
        if (request.petId() == null) {
            throw new RuntimeException("petId is required");
        }
        return createSearchGroup(
                request.petId(),
                request.groupName(),
                request.description(),
                request.status(),
                request.area(),
                request.city(),
                request.leaderName(),
                request.leaderPhone(),
                request.coverageRadiusKm(),
                authentication);
    }



    private ResponseEntity<SearchGroupDto> createSearchGroup(
            Long petId,
            String groupName,
            String description,
            String status,
            String area,
            String city,
            String leaderName,
            String leaderPhone,
            Double coverageRadiusKm,
            Authentication authentication) {
        User user = currentUser(authentication);
        Pet pet = petService.findById(petId);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getRole() != User.Role.ADMIN
                && (pet.getOwner() == null || !pet.getOwner().getId().equals(user.getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SearchGroupMapper.toDto(volunteerService.createSearchGroupForLostPet(
                        pet,
                        user,
                        groupName,
                        description,
                        status,
                        area,
                        city,
                        leaderName,
                        leaderPhone,
                        coverageRadiusKm)));
    }

    @GetMapping
    public List<SearchGroupDto> listAll() {
        return searchGroupRepository.findAll().stream()
                .map(SearchGroupMapper::toDto)
                .toList();
    }

    @GetMapping("/pet/{petId}")
    public List<SearchGroupDto> listForPet(@PathVariable Long petId) {
        return searchGroupRepository.findByPetId(petId).stream()
                .map(SearchGroupMapper::toDto)
                .toList();
    }

    @PostMapping("/{searchGroupId}/join")
    public ResponseEntity<VolunteerDto> join(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SearchGroupMapper.toDto(volunteerService.joinSearchGroup(user.getId(), searchGroupId)));
    }

    @PostMapping("/{searchGroupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUser(authentication);
        volunteerService.leaveSearchGroup(user.getId(), searchGroupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{searchGroupId}/volunteers")
    public List<VolunteerDto> volunteers(@PathVariable Long searchGroupId) {
        return volunteerService.getActiveVolunteersForGroup(searchGroupId).stream()
                .map(SearchGroupMapper::toDto)
                .toList();
    }

    @GetMapping("/my")
    public List<SearchGroupDto> myGroups(Authentication authentication) {
        User user = currentUser(authentication);
        return volunteerService.getUserSearchGroups(user.getId()).stream()
                .map(SearchGroupMapper::toDto)
                .toList();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
