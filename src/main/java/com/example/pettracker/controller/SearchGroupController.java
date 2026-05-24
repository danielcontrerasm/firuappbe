package com.example.pettracker.controller;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.UserService;
import com.example.pettracker.service.VolunteerService;
import java.util.List;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<SearchGroup> create(
            @RequestParam Long petId,
            @RequestParam(required = false) String groupName,
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
                .body(volunteerService.createSearchGroupForLostPet(pet, user, groupName));
    }

    @GetMapping("/pet/{petId}")
    public List<SearchGroup> listForPet(@PathVariable Long petId) {
        return searchGroupRepository.findByPetId(petId);
    }

    @PostMapping("/{searchGroupId}/join")
    public ResponseEntity<Volunteer> join(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(volunteerService.joinSearchGroup(user.getId(), searchGroupId));
    }

    @PostMapping("/{searchGroupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUser(authentication);
        volunteerService.leaveSearchGroup(user.getId(), searchGroupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{searchGroupId}/volunteers")
    public List<Volunteer> volunteers(@PathVariable Long searchGroupId) {
        return volunteerService.getActiveVolunteersForGroup(searchGroupId);
    }

    @GetMapping("/my")
    public List<SearchGroup> myGroups(Authentication authentication) {
        User user = currentUser(authentication);
        return volunteerService.getUserSearchGroups(user.getId());
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
