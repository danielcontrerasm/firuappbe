package com.example.pettracker.controller;

import com.example.pettracker.dto.SearchGroupMessageDto;
import com.example.pettracker.dto.SearchGroupMessageRequest;
import com.example.pettracker.service.SearchGroupChatService;
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
public class SearchGroupChatController {

    private final SearchGroupChatService searchGroupChatService;

    public SearchGroupChatController(SearchGroupChatService searchGroupChatService) {
        this.searchGroupChatService = searchGroupChatService;
    }

    @GetMapping("/{searchGroupId}/messages")
    public List<SearchGroupMessageDto> listMessages(
            @PathVariable Long searchGroupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return searchGroupChatService.listMessages(authentication, searchGroupId, page, size);
    }

    @PostMapping("/{searchGroupId}/messages")
    public ResponseEntity<SearchGroupMessageDto> addMessage(
            @PathVariable Long searchGroupId,
            @RequestBody SearchGroupMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(searchGroupChatService.addMessage(authentication, searchGroupId, request));
    }
}
