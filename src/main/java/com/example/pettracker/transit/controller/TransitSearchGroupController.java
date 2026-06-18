package com.example.pettracker.transit.controller;

import com.example.pettracker.entity.User;
import com.example.pettracker.service.UserService;
import com.example.pettracker.transit.dto.TransitSearchGroupCreateRequest;
import com.example.pettracker.transit.dto.TransitSearchGroupDto;
import com.example.pettracker.transit.entity.TransitSearchGroup;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.mapper.TransitMapper;
import com.example.pettracker.transit.service.TransitSearchGroupService;
import com.example.pettracker.transit.service.TransitVehicleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit/search-groups")
public class TransitSearchGroupController {

    private final TransitSearchGroupService transitSearchGroupService;
    private final TransitVehicleService transitVehicleService;
    private final UserService userService;

    public TransitSearchGroupController(
            TransitSearchGroupService transitSearchGroupService,
            TransitVehicleService transitVehicleService,
            UserService userService) {
        this.transitSearchGroupService = transitSearchGroupService;
        this.transitVehicleService = transitVehicleService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<TransitSearchGroupDto> create(
            @RequestBody TransitSearchGroupCreateRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(request.vehicleId());
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TransitSearchGroup savedGroup = transitSearchGroupService.create(vehicle, user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransitMapper.toDto(savedGroup));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<TransitSearchGroupDto>> listForVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(transitSearchGroupService.listForVehicle(vehicleId).stream()
                .map(TransitMapper::toDto)
                .toList());
    }

    @PostMapping("/{searchGroupId}/join")
    public ResponseEntity<TransitSearchGroupDto> join(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUser(authentication);
        transitSearchGroupService.joinSearchGroup(user.getId(), searchGroupId);
        TransitSearchGroup group = transitSearchGroupService.findById(searchGroupId);
        return group == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(TransitMapper.toDto(group));
    }

    @PostMapping("/{searchGroupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long searchGroupId, Authentication authentication) {
        User user = currentUser(authentication);
        transitSearchGroupService.leaveSearchGroup(user.getId(), searchGroupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public List<TransitSearchGroupDto> myGroups(Authentication authentication) {
        User user = currentUser(authentication);
        return transitSearchGroupService.listForUser(user.getId()).stream()
                .map(TransitMapper::toDto)
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
