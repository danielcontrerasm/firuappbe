package com.example.pettracker.transit.controller;

import com.example.pettracker.entity.User;
import com.example.pettracker.service.UserService;
import com.example.pettracker.transit.dto.TransitLocationDto;
import com.example.pettracker.transit.dto.TransitLocationRequest;
import com.example.pettracker.transit.dto.TransitReportDto;
import com.example.pettracker.transit.dto.TransitVehicleDto;
import com.example.pettracker.transit.dto.TransitVehicleRequest;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.mapper.TransitMapper;
import com.example.pettracker.transit.service.TransitLocationService;
import com.example.pettracker.transit.service.TransitReportService;
import com.example.pettracker.transit.service.TransitVehicleService;
import java.util.List;
import java.util.Locale;
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
@RequestMapping("/api/transit/vehicles")
public class TransitVehicleController {

    private final TransitVehicleService transitVehicleService;
    private final TransitLocationService transitLocationService;
    private final TransitReportService transitReportService;
    private final UserService userService;

    public TransitVehicleController(
            TransitVehicleService transitVehicleService,
            TransitLocationService transitLocationService,
            TransitReportService transitReportService,
            UserService userService) {
        this.transitVehicleService = transitVehicleService;
        this.transitLocationService = transitLocationService;
        this.transitReportService = transitReportService;
        this.userService = userService;
    }

    @GetMapping
    public List<TransitVehicleDto> list(Authentication authentication) {
        User user = currentUser(authentication);
        return transitVehicleService.listForUser(user).stream()
                .map(TransitMapper::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<TransitVehicleDto> create(@RequestBody TransitVehicleRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        User owner = resolveOwner(user, request.ownerId());
        TransitVehicle vehicle = TransitVehicle.builder()
                .label(request.label())
                .plateNumber(request.plateNumber())
                .routeCode(request.routeCode())
                .operatorName(request.operatorName())
                .type(parseType(request.type()))
                .status(parseStatus(request.status()))
                .owner(owner)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(TransitMapper.toDto(transitVehicleService.save(vehicle)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransitVehicleDto> getById(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(id);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(TransitMapper.toDto(vehicle));
    }

    @PostMapping("/{id}/locations")
    public ResponseEntity<TransitLocationDto> addLocation(
            @PathVariable Long id,
            @RequestBody TransitLocationRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(id);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(transitLocationService.save(vehicle, request));
    }

    @GetMapping("/{id}/locations/latest")
    public ResponseEntity<TransitLocationDto> latestLocation(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(id);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TransitLocationDto latestLocation = transitLocationService.getLatestDtoByVehicleId(id);
        return latestLocation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(latestLocation);
    }

    @GetMapping("/{id}/route")
    public ResponseEntity<List<TransitLocationDto>> route(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") int hours,
            Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(id);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transitLocationService.getRouteForLastHours(id, Math.max(hours, 1)));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<TransitReportDto> report(
            @PathVariable Long id,
            @RequestParam(defaultValue = "DAY") String window,
            Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(id);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transitReportService.buildReport(vehicle, window));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        if (currentUser.getRole() == User.Role.ADMIN && ownerId != null) {
            User owner = userService.findById(ownerId);
            if (owner == null) {
                throw new RuntimeException("Owner not found");
            }
            return owner;
        }
        return currentUser;
    }

    private TransitVehicle.VehicleType parseType(String value) {
        if (value == null || value.isBlank()) {
            return TransitVehicle.VehicleType.TAXI;
        }
        return TransitVehicle.VehicleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private TransitVehicle.VehicleStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return TransitVehicle.VehicleStatus.ACTIVE;
        }
        return TransitVehicle.VehicleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
