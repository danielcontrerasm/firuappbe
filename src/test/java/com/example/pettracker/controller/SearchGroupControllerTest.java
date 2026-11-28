package com.example.pettracker.controller;

import com.example.pettracker.dto.SearchGroupRejoinOptionDto;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.entity.VolunteerRejoinRequest.Status;
import com.example.pettracker.mapper.SearchGroupMapper;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.VolunteerRejoinRequestRepository;
import com.example.pettracker.repository.VolunteerRepository;
import com.example.pettracker.service.CurrentUserService;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.VolunteerService;
import com.example.pettracker.service.VolunteerService.JoinSearchGroupResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchGroupControllerTest {

    private static final long USER_ID = 7L;
    private static final long GROUP_ID = 35L;

    private final User user = User.builder().id(USER_ID).role(User.Role.USER).build();
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("volunteer@example.com", "");
    private final SearchGroup group = SearchGroup.builder().id(GROUP_ID).groupName("Find Milo").build();
    private final VolunteerService volunteerService = mock(VolunteerService.class);
    private final SearchGroupRepository groupRepository = mock(SearchGroupRepository.class);
    private final VolunteerRepository volunteerRepository = mock(VolunteerRepository.class);
    private final VolunteerRejoinRequestRepository rejoinRepository = mock(VolunteerRejoinRequestRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CurrentUserService users = mock(CurrentUserService.class);
        when(users.require(authentication)).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new SearchGroupController(
                volunteerService, users, mock(PetService.class), groupRepository, rejoinRepository,
                new SearchGroupMapper(volunteerRepository))).build();
    }

    @Test
    void joinReturnsCreatedForNewAndReactivatedMemberships() throws Exception {
        Volunteer membership = membership(group, true, false);
        when(volunteerService.joinSearchGroupWithResult(USER_ID, GROUP_ID)).thenReturn(
                new JoinSearchGroupResult(membership, false, false, false, null, false),
                new JoinSearchGroupResult(membership, false, true, false, null, false));

        join().andExpectAll(status().isCreated(), jsonPath("$.reactivated").value(false));
        join().andExpectAll(status().isCreated(), jsonPath("$.reactivated").value(true));
    }

    @Test
    void joinReturnsOkForExistingActiveMembership() throws Exception {
        when(volunteerService.joinSearchGroupWithResult(USER_ID, GROUP_ID)).thenReturn(
                new JoinSearchGroupResult(membership(group, true, false), true, false, false, null, false));

        join().andExpectAll(status().isOk(), jsonPath("$.alreadyMember").value(true));
    }

    @Test
    void joinReturnsAcceptedAndRetainsPendingRequestIdOnRetry() throws Exception {
        Volunteer membership = membership(group, false, true);
        when(volunteerService.joinSearchGroupWithResult(USER_ID, GROUP_ID)).thenReturn(
                new JoinSearchGroupResult(membership, false, false, true, 91L, false),
                new JoinSearchGroupResult(membership, false, false, true, 91L, true));

        join().andExpectAll(status().isAccepted(), jsonPath("$.pendingApproval").value(true),
                jsonPath("$.rejoinRequestId").value(91), jsonPath("$.requestAlreadyPending").value(false));
        join().andExpectAll(status().isAccepted(), jsonPath("$.pendingApproval").value(true),
                jsonPath("$.rejoinRequestId").value(91), jsonPath("$.requestAlreadyPending").value(true));
    }

    @Test
    void rejoinOptionsUseAuthenticatedUserAndExposeOnlySafeFields() throws Exception {
        PageRequest page = PageRequest.of(2, 100);
        when(rejoinRepository.findRejoinOptions(USER_ID, Status.PENDING, page)).thenReturn(List.of(
                new SearchGroupRejoinOptionDto(GROUP_ID, "Find Milo", 91L),
                new SearchGroupRejoinOptionDto(36L, "Find Lola", null)));

        mvc.perform(get("/api/search-groups/my/rejoin-options")
                        .param("page", "2").param("size", "999").principal(authentication)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpectAll(status().isOk(), jsonPath("$[0].groupId").value(GROUP_ID),
                        jsonPath("$[0].groupName").value("Find Milo"), jsonPath("$[0].pendingRequestId").value(91),
                        jsonPath("$[0].leaderPhone").doesNotHaveJsonPath(),
                        jsonPath("$[0].whatsappGroupLink").doesNotHaveJsonPath(),
                        jsonPath("$[1].pendingRequestId").value(nullValue()));
        verify(rejoinRepository).findRejoinOptions(USER_ID, Status.PENDING, page);
    }

    @Test
    void discoveryReturnsMembershipAndManagementFlagsForViewer() throws Exception {
        group.setCreatedBy(user);
        SearchGroup revokedGroup = SearchGroup.builder().id(36L).visibility("PUBLIC").build();
        when(groupRepository.findDiscoverableForUser(eq(USER_ID), any())).thenReturn(List.of(group, revokedGroup));
        when(volunteerRepository.findByUserIdAndSearchGroupIdIn(USER_ID, List.of(GROUP_ID, 36L)))
                .thenReturn(List.of(membership(group, true, false), membership(revokedGroup, false, true)));

        mvc.perform(get("/api/search-groups").principal(authentication).accept(MediaType.APPLICATION_JSON))
                .andExpectAll(status().isOk(), jsonPath("$[0].activeMember").value(true),
                        jsonPath("$[0].revokedMember").value(false), jsonPath("$[0].canClose").value(true),
                        jsonPath("$[0].canManageMembers").value(true), jsonPath("$[0].canViewRoster").value(false),
                        jsonPath("$[1].activeMember").value(false), jsonPath("$[1].revokedMember").value(true),
                        jsonPath("$[1].canManageMembers").value(false));
    }

    private ResultActions join() throws Exception {
        return mvc.perform(post("/api/search-groups/{id}/join", GROUP_ID).principal(authentication)
                .accept(MediaType.APPLICATION_JSON));
    }

    private Volunteer membership(SearchGroup searchGroup, boolean active, boolean revoked) {
        return Volunteer.builder().searchGroup(searchGroup).user(user).active(active).revoked(revoked).build();
    }
}
