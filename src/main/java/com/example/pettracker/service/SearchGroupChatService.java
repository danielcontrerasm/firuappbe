package com.example.pettracker.service;

import com.example.pettracker.dto.SearchGroupMessageDto;
import com.example.pettracker.dto.SearchGroupMessageRequest;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.SearchGroupMessage;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.SearchGroupMessageRepository;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.VolunteerRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchGroupChatService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final SearchGroupMessageRepository messageRepository;
    private final SearchGroupRepository searchGroupRepository;
    private final VolunteerRepository volunteerRepository;
    private final CurrentUserService currentUserService;

    public SearchGroupChatService(
            SearchGroupMessageRepository messageRepository,
            SearchGroupRepository searchGroupRepository,
            VolunteerRepository volunteerRepository,
            CurrentUserService currentUserService) {
        this.messageRepository = messageRepository;
        this.searchGroupRepository = searchGroupRepository;
        this.volunteerRepository = volunteerRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<SearchGroupMessageDto> listMessages(
            Authentication authentication,
            Long searchGroupId,
            int page,
            int size) {
        User current = currentUserService.require(authentication);
        SearchGroup group = findGroup(searchGroupId);
        requireChatAccess(current, group);

        return messageRepository.findBySearchGroupIdOrderByCreatedAtAsc(
                        searchGroupId,
                        pageRequest(page, size))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SearchGroupMessageDto addMessage(
            Authentication authentication,
            Long searchGroupId,
            SearchGroupMessageRequest request) {
        User current = currentUserService.require(authentication);
        SearchGroup group = findGroup(searchGroupId);
        requireChatAccess(current, group);

        if (request == null || request.body() == null || request.body().isBlank()) {
            throw new RuntimeException("Message body is required");
        }
        String body = request.body().trim();
        if (body.length() > 2000) {
            throw new RuntimeException("Message body cannot exceed 2000 characters");
        }

        SearchGroupMessage message = messageRepository.save(SearchGroupMessage.builder()
                .searchGroup(group)
                .sender(current)
                .body(body)
                .createdAt(Instant.now())
                .build());
        return toDto(message);
    }

    private SearchGroup findGroup(Long searchGroupId) {
        return searchGroupRepository.findById(searchGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search group not found"));
    }

    private void requireChatAccess(User current, SearchGroup group) {
        if (current.getRole() == User.Role.ADMIN) {
            return;
        }

        boolean creator = group.getCreatedBy() != null
                && current.getId().equals(group.getCreatedBy().getId());
        Pet pet = group.getPet();
        boolean petOwner = pet != null
                && pet.getOwner() != null
                && current.getId().equals(pet.getOwner().getId());
        Volunteer membership = volunteerRepository.findByUserIdAndSearchGroupId(current.getId(), group.getId());
        boolean activeMember = membership != null && Boolean.TRUE.equals(membership.getActive());

        if (!creator && !petOwner && !activeMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only active group members can access the group chat");
        }
    }

    private Pageable pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    private SearchGroupMessageDto toDto(SearchGroupMessage message) {
        User sender = message.getSender();
        SearchGroup group = message.getSearchGroup();
        return new SearchGroupMessageDto(
                message.getId(),
                group == null ? null : group.getId(),
                sender == null ? null : sender.getId(),
                sender == null ? null : sender.getName(),
                message.getBody(),
                message.getCreatedAt());
    }
}
