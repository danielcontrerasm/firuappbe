package com.example.pettracker.transit.controller;

import com.example.pettracker.entity.User;
import com.example.pettracker.service.UserService;
import com.example.pettracker.transit.dto.TransitScheduledGeofenceDto;
import com.example.pettracker.transit.dto.TransitScheduledGeofenceRequest;
import com.example.pettracker.transit.entity.TransitScheduledGeofence;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.mapper.TransitMapper;
import com.example.pettracker.transit.service.TransitGeofencingService;
import com.example.pettracker.transit.service.TransitVehicleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit/geofences")
public class TransitGeofenceController {

    private final TransitGeofencingService transitGeofencingService;
    private final TransitVehicleService transitVehicleService;
    private final UserService userService;

    public TransitGeofenceController(
            TransitGeofencingService transitGeofencingService,
            TransitVehicleService transitVehicleService,
            UserService userService) {
        this.transitGeofencingService = transitGeofencingService;
        this.transitVehicleService = transitVehicleService;
        this.userService = userService;
    }

    @PostMapping("/vehicle/{vehicleId}")
    public ResponseEntity<TransitScheduledGeofenceDto> create(
            @PathVariable Long vehicleId,
            @RequestBody TransitScheduledGeofenceRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(vehicleId);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TransitScheduledGeofence savedGeofence = transitGeofencingService.createGeofence(vehicle, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransitMapper.toDto(savedGeofence));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<TransitScheduledGeofenceDto>> list(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        User user = currentUser(authentication);
        TransitVehicle vehicle = transitVehicleService.findById(vehicleId);
        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }
        if (!transitVehicleService.canAccess(user, vehicle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transitGeofencingService.getVehicleGeofences(vehicleId).stream()
                .map(TransitMapper::toDto)
                .toList());
    }

    @DeleteMapping("/{geofenceId}")
    public ResponseEntity<Void> delete(@PathVariable Long geofenceId, Authentication authentication) {
        User user = currentUser(authentication);
        TransitScheduledGeofence geofence = transitGeofencingService.findById(geofenceId);
        if (geofence == null) {
            return ResponseEntity.notFound().build();
        }
        if (geofence.getVehicle() == null || !transitVehicleService.canAccess(user, geofence.getVehicle())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        transitGeofencingService.deleteGeofence(geofenceId);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
