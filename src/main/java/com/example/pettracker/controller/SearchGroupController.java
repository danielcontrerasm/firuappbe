package com.example.pettracker.controller;

import com.example.pettracker.dto.SearchGroupResponse;
import com.example.pettracker.repository.SearchGroupRepository;
import com.example.pettracker.repository.SearchVolunteerRepository;
import com.example.pettracker.service.PetMapper;
import com.example.pettracker.service.PetTrackingService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-groups")
public class SearchGroupController {

    private final PetTrackingService petTrackingService;
    private final SearchGroupRepository searchGroupRepository;
    private final SearchVolunteerRepository searchVolunteerRepository;
    private final PetMapper petMapper;

    public SearchGroupController(
            PetTrackingService petTrackingService,
            SearchGroupRepository searchGroupRepository,
            SearchVolunteerRepository searchVolunteerRepository,
            PetMapper petMapper) {
        this.petTrackingService = petTrackingService;
        this.searchGroupRepository = searchGroupRepository;
        this.searchVolunteerRepository = searchVolunteerRepository;
        this.petMapper = petMapper;
    }

    @PostMapping("/{searchGroupId}/join")
    public SearchGroupResponse join(@PathVariable Long searchGroupId) {
        petTrackingService.joinSearchGroup(searchGroupId);
        var group = searchGroupRepository.findById(searchGroupId)
                .orElseThrow(() -> new IllegalArgumentException("Search group not found"));
        return petMapper.toSearchGroup(group, searchVolunteerRepository.findBySearchGroupId(searchGroupId));
    }
}
