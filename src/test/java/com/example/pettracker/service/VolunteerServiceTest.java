package com.example.pettracker.service;

import com.example.pettracker.dto.SearchGroupUpdateRequest;
import com.example.pettracker.dto.VolunteerCandidateDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.repository.VolunteerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VolunteerServiceTest {

    private VolunteerRepository volunteerRepository;
    private SearchGroupRepository searchGroupRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private VolunteerService volunteerService;

    @BeforeEach
    void setUp() {
        volunteerRepository = mock(VolunteerRepository.class);
        searchGroupRepository = mock(SearchGroupRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        volunteerService = new VolunteerService(
                volunteerRepository,
                searchGroupRepository,
                userRepository,
                notificationService);

        when(searchGroupRepository.save(any(SearchGroup.class))).thenAnswer(invocation -> {
            SearchGroup group = invocation.getArgument(0);
            if (group.getId() == null) {
                group.setId(50L);
            }
            return group;
        });
        when(volunteerRepository.save(any(Volunteer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAddsCreatorAndRequestedVolunteers() {
        User owner = user(1L, "Owner", "owner@example.com");
        User member = user(2L, "Member", "member@example.com");
        Pet pet = Pet.builder().id(10L).name("Milo").owner(owner).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        SearchGroup group = volunteerService.createSearchGroupForLostPet(
                pet,
                owner,
                "Find Milo",
                "North side search",
                "active",
                "North",
                "Bogota",
                null,
                null,
                4.0,
                List.of(2L, 2L));

        assertEquals(2, group.getVolunteers().size());
        assertTrue(group.getVolunteers().contains(owner));
        assertTrue(group.getVolunteers().contains(member));
        verify(notificationService).sendEmail(
                "member@example.com",
                "Pet search group",
                "You were added to search group 'Find Milo'");
    }

    @Test
    void joinReactivatesExistingMembership() {
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).groupName("Find Milo").build();
        Volunteer existing = Volunteer.builder()
                .id(80L)
                .user(member)
                .searchGroup(group)
                .active(false)
                .joinedAt(Instant.EPOCH)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(existing);

        Volunteer result = volunteerService.joinSearchGroup(2L, 50L);

        assertSame(existing, result);
        assertTrue(result.getActive());
        assertFalse(result.getJoinedAt().equals(Instant.EPOCH));
        assertTrue(group.getVolunteers().contains(member));
    }

    @Test
    void creatorCanUpdateGroup() {
        User owner = user(1L, "Owner", "owner@example.com");
        SearchGroup group = SearchGroup.builder()
                .id(50L)
                .createdBy(owner)
                .groupName("Old name")
                .status("active")
                .build();
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));

        SearchGroup updated = volunteerService.updateSearchGroup(
                50L,
                owner,
                new SearchGroupUpdateRequest(
                        "New name", null, "paused", null, "Bogota", null, null, 2.5));

        assertEquals("New name", updated.getGroupName());
        assertEquals("paused", updated.getStatus());
        assertEquals("Bogota", updated.getCity());
        assertEquals(2.5, updated.getCoverageRadiusKm());
    }

    @Test
    void unrelatedUserCannotAddMember() {
        User owner = user(1L, "Owner", "owner@example.com");
        User unrelatedUser = user(3L, "Other", "other@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).build();
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));

        assertThrows(
                ResponseStatusException.class,
                () -> volunteerService.addVolunteer(50L, 2L, unrelatedUser));
        verify(userRepository, never()).findById(2L);
    }

    @Test
    void creatorCanSearchForUsersWhoAreNotMembers() {
        User owner = user(1L, "Owner", "owner@example.com");
        User candidate = user(2L, "Daniel Candidate", "daniel@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).build();
        Pageable pageable = Pageable.ofSize(20);
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));
        VolunteerCandidateDto candidateDto = new VolunteerCandidateDto(
                candidate.getId(), candidate.getName(), candidate.getEmail());
        when(userRepository.searchVolunteerCandidates(50L, "daniel", pageable)).thenReturn(List.of(candidateDto));

        List<VolunteerCandidateDto> results = volunteerService.searchVolunteerCandidates(
                50L,
                "  daniel  ",
                owner,
                pageable);

        assertEquals(List.of(candidateDto), results);
    }

    @Test
    void candidateSearchRequiresAtLeastTwoCharacters() {
        User owner = user(1L, "Owner", "owner@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).build();
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));

        assertThrows(
                RuntimeException.class,
                () -> volunteerService.searchVolunteerCandidates(50L, "d", owner, Pageable.ofSize(20)));
        verify(userRepository, never()).searchVolunteerCandidates(any(), any(), any());
    }

    @Test
    void unrelatedUserCannotSearchMemberCandidates() {
        User owner = user(1L, "Owner", "owner@example.com");
        User unrelatedUser = user(3L, "Other", "other@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).build();
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));

        assertThrows(
                ResponseStatusException.class,
                () -> volunteerService.searchVolunteerCandidates(
                        50L, "daniel", unrelatedUser, Pageable.ofSize(20)));
        verify(userRepository, never()).searchVolunteerCandidates(any(), any(), any());
    }

    private User user(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password("password")
                .role(User.Role.USER)
                .build();
    }
}
