package com.example.pettracker.controller;

import com.example.pettracker.dto.GeofenceRequests.*;
import com.example.pettracker.dto.GeofenceResponseDto;
import com.example.pettracker.service.GeofencingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {
    private final GeofencingService geofencingService;

    public GeofenceController(GeofencingService geofencingService) {
        this.geofencingService = geofencingService;
    }

    @PostMapping("/circle/{petId}")
    public ResponseEntity<GeofenceResponseDto> createCircle(
            @PathVariable Long petId,
            @RequestBody CircleRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(geofencingService.createCircle(petId, req, authentication));
    }

    @PostMapping("/polygon/{petId}")
    public ResponseEntity<GeofenceResponseDto> createPolygon(
            @PathVariable Long petId,
            @RequestBody PolygonRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(geofencingService.createPolygon(petId, req, authentication));
    }

    @GetMapping("/{petId}")
    public ResponseEntity<GeofenceResponseDto> getByPet(@PathVariable Long petId, Authentication authentication) {
        return ResponseEntity.ok(geofencingService.getByPet(petId, authentication));
    }

    @DeleteMapping("/{petId}")
    public ResponseEntity<Void> delete(@PathVariable Long petId, Authentication authentication) {
        geofencingService.deleteByPet(petId, authentication);
        return ResponseEntity.noContent().build();
    }
}
