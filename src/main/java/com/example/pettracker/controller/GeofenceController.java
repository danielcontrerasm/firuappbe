package com.example.pettracker.controller;

import com.example.pettracker.dto.GeofenceRequests.*;
import com.example.pettracker.dto.GeofenceResponseDto;
import com.example.pettracker.entity.Geofence;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.GeofenceMapper;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.service.CurrentUserService;
import com.example.pettracker.service.GeofencingService;
import org.locationtech.jts.geom.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {
    private final GeofenceRepository geofenceRepository;
    private final PetRepository petRepository;
    private final LocationRepository locationRepository;
    private final GeofencingService geofencingService;
    private final CurrentUserService currentUserService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public GeofenceController(
            GeofenceRepository geofenceRepository,
            PetRepository petRepository,
            LocationRepository locationRepository,
            GeofencingService geofencingService,
            CurrentUserService currentUserService) {
        this.geofenceRepository = geofenceRepository;
        this.petRepository = petRepository;
        this.locationRepository = locationRepository;
        this.geofencingService = geofencingService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/circle/{petId}")
    public ResponseEntity<GeofenceResponseDto> createCircle(
            @PathVariable Long petId,
            @RequestBody CircleRequest req,
            Authentication authentication) {
        Pet pet = petRepository.findById(petId).orElse(null);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(authentication, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Geofence g = Geofence.builder()
                .pet(pet)
                .type(Geofence.Type.CIRCLE)
                .centerLat(req.getCenterLat())
                .centerLng(req.getCenterLng())
                .radiusMeters(req.getRadiusMeters())
                .build();
        Geofence saved = geofenceRepository.save(g);
        checkLatestLocationForNewGeofence(petId);
        return ResponseEntity.ok(GeofenceMapper.toDto(saved));
    }

    @PostMapping("/polygon/{petId}")
    public ResponseEntity<GeofenceResponseDto> createPolygon(
            @PathVariable Long petId,
            @RequestBody PolygonRequest req,
            Authentication authentication) {
        Pet pet = petRepository.findById(petId).orElse(null);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(authentication, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // coordinates are list of "lat,lng" strings
        List<List<Double>> coords = req.getCoordinates();
        Coordinate[] c = new Coordinate[coords.size() + 1];

        for (int i = 0; i < coords.size(); i++) {
            double lat = coords.get(i).get(0);
            double lng = coords.get(i).get(1);
            c[i] = new Coordinate(lng, lat); // note order: lng, lat for geometry
        }
        c[coords.size()] = c[0]; // close polygon

        Polygon poly = geometryFactory.createPolygon(c);
        Geofence g = Geofence.builder()
                .pet(pet)
                .type(Geofence.Type.POLYGON)
                .polygon(poly)
                .build();


        Geofence saved = geofenceRepository.save(g);
        checkLatestLocationForNewGeofence(petId);
        return ResponseEntity.ok(GeofenceMapper.toDto(saved));
    }

    @GetMapping("/{petId}")
    public ResponseEntity<GeofenceResponseDto> getByPet(@PathVariable Long petId, Authentication authentication) {
        Pet pet = petRepository.findById(petId).orElse(null);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(authentication, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return geofenceRepository.findByPetId(petId)
                .map(GeofenceMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{petId}")
    public ResponseEntity<Void> delete(@PathVariable Long petId, Authentication authentication) {
        Pet pet = petRepository.findById(petId).orElse(null);
        if (pet == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessPet(authentication, pet)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var opt = geofenceRepository.findByPetId(petId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        geofenceRepository.delete(opt.get());
        return ResponseEntity.noContent().build();
    }

    private void checkLatestLocationForNewGeofence(Long petId) {
        locationRepository.findTopByPetIdOrderByTimestampDesc(petId)
                .ifPresent(location -> {
                    geofencingService.checkAndAlert(location);
                    geofencingService.checkGeofence(location);
                });
    }

    private boolean canAccessPet(Authentication authentication, Pet pet) {
        User current = currentUserService.require(authentication);
        return current.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null && current.getId().equals(pet.getOwner().getId()));
    }
}
