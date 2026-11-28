package com.example.pettracker.service;

import com.example.pettracker.dto.SearchGroupUpdateRequest;
import com.example.pettracker.dto.VolunteerCandidateDto;
import com.example.pettracker.dto.VolunteerRejoinRequestDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.entity.VolunteerRejoinRequest;
import com.example.pettracker.entity.VolunteerRejoinRequest.Status;
import com.example.pettracker.event.VolunteerRejoinRequestedEvent;
import com.example.pettracker.event.VolunteerRejoinResolvedEvent;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.repository.VolunteerRepository;
import com.example.pettracker.repository.VolunteerRejoinRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VolunteerServiceTest {

    private VolunteerRepository volunteerRepository;
    private VolunteerRejoinRequestRepository rejoinRequestRepository;
    private SearchGroupRepository searchGroupRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private ImageUploadLimits imageUploadLimits;
    private ApplicationEventPublisher eventPublisher;
    private VolunteerService volunteerService;

    @BeforeEach
    void setUp() {
        volunteerRepository = mock(VolunteerRepository.class);
        rejoinRequestRepository = mock(VolunteerRejoinRequestRepository.class);
        searchGroupRepository = mock(SearchGroupRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        imageUploadLimits = mock(ImageUploadLimits.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        volunteerService = new VolunteerService(
                volunteerRepository,
                rejoinRequestRepository,
                searchGroupRepository,
                userRepository,
                notificationService,
                imageUploadLimits,
                eventPublisher);

        when(searchGroupRepository.save(any(SearchGroup.class))).thenAnswer(invocation -> {
            SearchGroup group = invocation.getArgument(0);
            if (group.getId() == null) {
                group.setId(50L);
            }
            return group;
        });
        when(volunteerRepository.save(any(Volunteer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rejoinRequestRepository.save(any(VolunteerRejoinRequest.class))).thenAnswer(invocation -> {
            VolunteerRejoinRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(700L);
            }
            return request;
        });
    }

    @Test
    void createAddsCreatorAndRequestedVolunteers() {
        User owner = user(1L, "Owner", "owner@example.com");
        owner.setPhone("+573001234567");
        User member = user(2L, "Member", "member@example.com");
        Pet pet = Pet.builder().id(10L).name("Milo").owner(owner).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(owner));
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

        assertNull(group.getLeaderPhone());
        org.mockito.ArgumentCaptor<Volunteer> membershipCaptor = org.mockito.ArgumentCaptor.forClass(Volunteer.class);
        verify(volunteerRepository, times(2)).save(membershipCaptor.capture());
        assertEquals(List.of(owner.getId(), member.getId()), membershipCaptor.getAllValues().stream()
                .map(membership -> membership.getUser().getId())
                .toList());
    }

    @Test
    void creationLocksCreatorBeforeCheckingPlanQuota() {
        User owner = user(1L, "Owner", "owner@example.com");
        Pet pet = Pet.builder().id(10L).name("Milo").owner(owner).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        when(searchGroupRepository.countByCreatedById(1L)).thenReturn(1L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> volunteerService.createSearchGroupForLostPet(
                        pet, owner, "Find Milo", null, null, null, null, null, null, null, null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        var checks = inOrder(userRepository, searchGroupRepository);
        checks.verify(userRepository).findByIdForUpdate(1L);
        checks.verify(searchGroupRepository).countByCreatedById(1L);
        verify(searchGroupRepository, never()).save(any(SearchGroup.class));
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
        group.setVisibility("PUBLIC");
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(existing);

        Volunteer result = volunteerService.joinSearchGroup(2L, 50L);

        assertSame(existing, result);
        assertTrue(result.getActive());
        assertFalse(result.getJoinedAt().equals(Instant.EPOCH));
        assertFalse(result.getRevoked());
    }

    @Test
    void joinReportsAlreadyActiveMembershipWithoutCreatingAnotherRecord() {
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).groupName("Find Milo").build();
        Volunteer existing = Volunteer.builder()
                .id(80L)
                .user(member)
                .searchGroup(group)
                .active(true)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(existing);

        VolunteerService.JoinSearchGroupResult result = volunteerService.joinSearchGroupWithResult(2L, 50L);

        assertSame(existing, result.volunteer());
        assertTrue(result.alreadyMember());
        assertFalse(result.reactivated());
        verify(volunteerRepository, never()).save(any(Volunteer.class));
    }

    @Test
    void unrelatedUserCannotJoinPrivateGroupByGuessingItsId() {
        User owner = user(1L, "Owner", "owner@example.com");
        User outsider = user(2L, "Outsider", "outsider@example.com");
        SearchGroup group = SearchGroup.builder()
                .id(50L)
                .createdBy(owner)
                .groupName("Find Milo")
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(outsider));
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> volunteerService.joinSearchGroupWithResult(2L, 50L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(volunteerRepository, never()).save(any(Volunteer.class));
    }

    @Test
    void privateClosedGroupAndMissingGroupGiveOutsiderSameJoinResponse() {
        User outsider = user(2L, "Outsider", "outsider@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).status(SearchGroup.STATUS_CLOSED).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(outsider));
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));

        ResponseStatusException closed = assertThrows(ResponseStatusException.class,
                () -> volunteerService.joinSearchGroupWithResult(2L, 50L));
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> volunteerService.joinSearchGroupWithResult(2L, 51L));

        assertEquals(HttpStatus.NOT_FOUND, closed.getStatusCode());
        assertEquals(missing.getStatusCode(), closed.getStatusCode());
        assertEquals(missing.getReason(), closed.getReason());
        verify(volunteerRepository, never()).save(any(Volunteer.class));
    }

    @Test
    void revokedMemberCannotSubmitRejoinRequestForClosedGroup() {
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).status(SearchGroup.STATUS_CLOSED).build();
        Volunteer membership = Volunteer.builder()
                .id(80L).user(member).searchGroup(group).active(false).revoked(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(membership);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> volunteerService.joinSearchGroupWithResult(2L, 50L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(rejoinRequestRepository, never()).save(any(VolunteerRejoinRequest.class));
    }

    @Test
    void privateGroupWithNoMembershipAndMissingGroupGiveSameLeaveResponse() {
        SearchGroup group = SearchGroup.builder().id(50L).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));

        ResponseStatusException unrelated = assertThrows(ResponseStatusException.class,
                () -> volunteerService.leaveSearchGroup(2L, 50L));
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> volunteerService.leaveSearchGroup(2L, 51L));

        assertEquals(HttpStatus.NOT_FOUND, unrelated.getStatusCode());
        assertEquals(missing.getStatusCode(), unrelated.getStatusCode());
        assertEquals(missing.getReason(), unrelated.getReason());
        verify(volunteerRepository, never()).save(any(Volunteer.class));
    }

    @Test
    void creatorCannotLeaveButAnActiveMemberCanLeaveAClosedGroup() {
        User creator = user(1L, "Creator", "creator@example.com");
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(creator).status(SearchGroup.STATUS_CLOSED).build();
        Volunteer membership = Volunteer.builder().id(80L).user(member).searchGroup(group).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(membership);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> volunteerService.leaveSearchGroup(1L, 50L));
        volunteerService.leaveSearchGroup(2L, 50L);

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertFalse(membership.getActive());
        assertFalse(membership.getRevoked());
        verify(volunteerRepository).save(membership);
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
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));

        SearchGroup updated = volunteerService.updateSearchGroup(
                50L,
                owner,
                new SearchGroupUpdateRequest(
                        "  New name  ", null, "closed", null, "Bogota", null, null, 2.5, null));

        assertEquals("New name", updated.getGroupName());
        assertEquals(SearchGroup.STATUS_CLOSED, updated.getStatus());
        assertEquals("Bogota", updated.getCity());
        assertEquals(2.5, updated.getCoverageRadiusKm());
    }

    @Test
    void groupUpdateRejectsANameLongerThanTheColumn() {
        User owner = user(1L, "Owner", "owner@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> volunteerService.updateSearchGroup(50L, owner,
                        new SearchGroupUpdateRequest("a".repeat(256), null, null, null, null, null, null, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(searchGroupRepository, never()).save(any(SearchGroup.class));
    }

    @Test
    void unrelatedUserCannotAddMember() {
        User owner = user(1L, "Owner", "owner@example.com");
        User unrelatedUser = user(3L, "Other", "other@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));

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

    @Test
    void closedGroupsRejectCandidateSearch() {
        User owner = user(1L, "Owner", "owner@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(owner).status(SearchGroup.STATUS_CLOSED).build();
        when(searchGroupRepository.findById(50L)).thenReturn(Optional.of(group));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> volunteerService.searchVolunteerCandidates(50L, "daniel", owner, Pageable.ofSize(20)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).searchVolunteerCandidates(any(), any(), any());
    }

    @Test
    void revokedMemberCreatesPendingRequestAndNotifiesCreator() {
        User creator = user(1L, "Creator", "creator@example.com");
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder()
                .id(50L)
                .createdBy(creator)
                .groupName("Find Milo")
                .visibility("PRIVATE")
                .status(SearchGroup.STATUS_ACTIVE)
                .build();
        Volunteer revokedMembership = Volunteer.builder()
                .id(80L)
                .user(member)
                .searchGroup(group)
                .active(false)
                .revoked(true)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(revokedMembership);

        VolunteerService.JoinSearchGroupResult result = volunteerService.joinSearchGroupWithResult(2L, 50L);

        assertTrue(result.pendingApproval());
        assertFalse(result.alreadyMember());
        assertFalse(result.reactivated());
        assertEquals(700L, result.rejoinRequestId());
        assertFalse(result.requestAlreadyPending());
        assertFalse(revokedMembership.getActive());
        assertTrue(revokedMembership.getRevoked());
        verify(rejoinRequestRepository).save(any(VolunteerRejoinRequest.class));
        verify(eventPublisher).publishEvent(new VolunteerRejoinRequestedEvent(700L));
        verify(volunteerRepository, never()).save(any(Volunteer.class));
    }

    @Test
    void repeatedRevokedJoinReusesPendingRequestWithoutSendingAnotherNotification() {
        User member = user(2L, "Member", "member@example.com");
        User creator = user(1L, "Creator", "creator@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(creator).groupName("Find Milo").build();
        Volunteer revokedMembership = Volunteer.builder()
                .id(80L).user(member).searchGroup(group).active(false).revoked(true).build();
        VolunteerRejoinRequest pendingRequest = VolunteerRejoinRequest.builder()
                .id(701L)
                .volunteer(revokedMembership)
                .searchGroup(group)
                .status(Status.PENDING)
                .createdAt(Instant.EPOCH)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(revokedMembership);
        when(rejoinRequestRepository.findFirstByVolunteerIdAndStatusOrderByCreatedAtDesc(80L, Status.PENDING))
                .thenReturn(Optional.of(pendingRequest));

        VolunteerService.JoinSearchGroupResult result = volunteerService.joinSearchGroupWithResult(2L, 50L);

        assertTrue(result.pendingApproval());
        assertTrue(result.requestAlreadyPending());
        assertEquals(701L, result.rejoinRequestId());
        verify(rejoinRequestRepository, never()).save(any(VolunteerRejoinRequest.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void groupCreatorCanApproveRejoinRequestAndReactivateMembership() {
        User creator = user(1L, "Creator", "creator@example.com");
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(creator).status(SearchGroup.STATUS_ACTIVE).build();
        Volunteer revokedMembership = Volunteer.builder()
                .id(80L).user(member).searchGroup(group).active(false).revoked(true).joinedAt(Instant.EPOCH).build();
        VolunteerRejoinRequest pendingRequest = VolunteerRejoinRequest.builder()
                .id(700L).volunteer(revokedMembership).searchGroup(group).status(Status.PENDING).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(rejoinRequestRepository.findByIdAndSearchGroupId(700L, 50L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));

        VolunteerRejoinRequestDto result = volunteerService.approveRejoinRequest(50L, 700L, creator);

        assertEquals("APPROVED", result.status());
        assertTrue(revokedMembership.getActive());
        assertFalse(revokedMembership.getRevoked());
        assertFalse(revokedMembership.getJoinedAt().equals(Instant.EPOCH));
        assertEquals(Status.APPROVED, pendingRequest.getStatus());
        assertSame(creator, pendingRequest.getReviewedBy());
        verify(volunteerRepository).save(revokedMembership);
        verify(eventPublisher).publishEvent(new VolunteerRejoinResolvedEvent(700L));
    }

    @Test
    void groupCreatorCanRejectRejoinRequestWithoutReactivatingMembership() {
        User creator = user(1L, "Creator", "creator@example.com");
        User member = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(creator).build();
        Volunteer revokedMembership = Volunteer.builder()
                .id(80L).user(member).searchGroup(group).active(false).revoked(true).build();
        VolunteerRejoinRequest pendingRequest = VolunteerRejoinRequest.builder()
                .id(700L).volunteer(revokedMembership).searchGroup(group).status(Status.PENDING).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(rejoinRequestRepository.findByIdAndSearchGroupId(700L, 50L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));

        VolunteerRejoinRequestDto result = volunteerService.rejectRejoinRequest(50L, 700L, creator);

        assertEquals("REJECTED", result.status());
        assertEquals(Status.REJECTED, pendingRequest.getStatus());
        assertFalse(revokedMembership.getActive());
        assertTrue(revokedMembership.getRevoked());
        verify(volunteerRepository, never()).save(any(Volunteer.class));
        verify(eventPublisher).publishEvent(new VolunteerRejoinResolvedEvent(700L));
    }

    @Test
    void nonCreatorCannotApproveOrRejectRejoinRequest() {
        User creator = user(1L, "Creator", "creator@example.com");
        User otherUser = user(2L, "Other", "other@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(creator).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));

        ResponseStatusException approveException = assertThrows(
                ResponseStatusException.class,
                () -> volunteerService.approveRejoinRequest(50L, 700L, otherUser));
        ResponseStatusException rejectException = assertThrows(
                ResponseStatusException.class,
                () -> volunteerService.rejectRejoinRequest(50L, 700L, otherUser));

        assertEquals(HttpStatus.NOT_FOUND, approveException.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rejectException.getStatusCode());
        verify(rejoinRequestRepository, never()).findByIdAndSearchGroupId(any(), any());
        verify(rejoinRequestRepository, never()).save(any(VolunteerRejoinRequest.class));
    }

    @Test
    void managerCannotBypassCreatorReviewByAddingRevokedMemberDirectly() {
        User creator = user(1L, "Creator", "creator@example.com");
        User admin = user(3L, "Admin", "admin@example.com");
        admin.setRole(User.Role.ADMIN);
        User revokedMember = user(2L, "Member", "member@example.com");
        SearchGroup group = SearchGroup.builder().id(50L).createdBy(creator).build();
        Volunteer membership = Volunteer.builder()
                .id(80L).user(revokedMember).searchGroup(group).active(false).revoked(true).build();
        when(searchGroupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(revokedMember));
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(membership);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> volunteerService.addVolunteer(50L, 2L, admin));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(volunteerRepository, never()).save(any(Volunteer.class));
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
