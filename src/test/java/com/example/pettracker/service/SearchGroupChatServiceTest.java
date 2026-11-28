package com.example.pettracker.service;

import com.example.pettracker.dto.SearchGroupMessageRequest;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.SearchGroupMessage;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.SearchGroupMessageRepository;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.VolunteerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchGroupChatServiceTest {

    private final SearchGroupMessageRepository messageRepository = mock(SearchGroupMessageRepository.class);
    private final SearchGroupRepository groupRepository = mock(SearchGroupRepository.class);
    private final VolunteerRepository volunteerRepository = mock(VolunteerRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final SearchGroupChatService service = new SearchGroupChatService(
            messageRepository, groupRepository, volunteerRepository, currentUserService);

    private final Authentication authentication = mock(Authentication.class);
    private final User member = User.builder().id(2L).name("Member").role(User.Role.USER).build();
    private final SearchGroup group = SearchGroup.builder().id(50L).build();
    private final Volunteer membership = Volunteer.builder().user(member).searchGroup(group).build();

    @BeforeEach
    void setUp() {
        when(currentUserService.require(authentication)).thenReturn(member);
        when(volunteerRepository.findByUserIdAndSearchGroupId(2L, 50L)).thenReturn(membership);
    }

    @Test
    void sendLocksGroupBeforeCheckingMembershipAndSavingMessage() {
        when(groupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        when(messageRepository.save(any(SearchGroupMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var message = service.addMessage(authentication, 50L, new SearchGroupMessageRequest("  Found a lead  "));

        assertEquals("Found a lead", message.body());
        var calls = inOrder(groupRepository, volunteerRepository, messageRepository);
        calls.verify(groupRepository).findByIdForUpdate(50L);
        calls.verify(volunteerRepository).findByUserIdAndSearchGroupId(2L, 50L);
        calls.verify(messageRepository).save(any(SearchGroupMessage.class));
    }

    @Test
    void closedGroupStillAllowsMemberToReadHistoryButRejectsNewMessages() {
        group.setStatus(SearchGroup.STATUS_CLOSED);
        when(groupRepository.findById(50L)).thenReturn(Optional.of(group));
        when(groupRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(group));
        SearchGroupMessage oldMessage = SearchGroupMessage.builder()
                .id(9L).searchGroup(group).sender(member).body("Earlier update").build();
        when(messageRepository.findBySearchGroupIdOrderByCreatedAtAscIdAsc(50L, PageRequest.of(0, 50)))
                .thenReturn(List.of(oldMessage));

        var messages = service.listMessages(authentication, 50L, 0, 50);
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.addMessage(authentication, 50L, new SearchGroupMessageRequest("New update")));

        assertEquals(List.of("Earlier update"), messages.stream().map(message -> message.body()).toList());
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(messageRepository, never()).save(any(SearchGroupMessage.class));
    }
}
