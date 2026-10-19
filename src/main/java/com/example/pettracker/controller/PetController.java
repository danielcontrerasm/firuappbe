package com.example.pettracker.controller;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.LocationRequest;
import com.example.pettracker.dto.PetDto;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.dto.PetWalkDistanceDayDto;
import com.example.pettracker.service.PetService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/locations")
    public List <LocationDTO> findLastLocationsByUserId ( Authentication authentication){
        return petService.findLastLocationsForCurrentUser(authentication);
    }

    @GetMapping
    public List<PetDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return petService.listForCurrentUser(authentication, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(petService.getForCurrentUser(id, authentication));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetDto> create(@RequestBody PetDto request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(petService.create(request, null, authentication));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PetDto> createWithImage(
            @RequestPart("pet") PetDto request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(petService.create(request, image, authentication));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetDto> update(@PathVariable Long id, @RequestBody PetDto request, Authentication authentication) {
        return ResponseEntity.ok(petService.update(id, request, null, authentication));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PetDto> updateWithImage(
            @PathVariable Long id,
            @RequestPart("pet") PetDto request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        return ResponseEntity.ok(petService.update(id, request, image, authentication));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id, Authentication authentication) {
        PetService.PetImage image = petService.findAccessibleImageById(id, authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.fileNameOrDefault() + "\"")
                .body(image.data());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        petService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lost")
    public ResponseEntity<PetDto> markLost(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(petService.markPetAsLost(id, authentication));
    }

    @PostMapping("/{id}/found")
    public ResponseEntity<PetDto> markFound(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(petService.markPetAsFound(id, authentication));
    }

    @GetMapping("/{id}/locations")
    public ResponseEntity<List<LocationDTO>> locations(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(petService.getLocations(id, authentication));
    }

    @GetMapping("/{id}/route")
    public ResponseEntity<List<LocationDTO>> routeLast3Hours(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(petService.getRouteLast3Hours(id, authentication));
    }

    @GetMapping("/{id}/route/range")
    public ResponseEntity<List<LocationDTO>> routeBetween(
            @PathVariable Long id,
            @RequestParam String from,
            @RequestParam String to,
            Authentication authentication) {
        return ResponseEntity.ok(petService.getRouteBetween(id, from, to, authentication));
    }

    @GetMapping("/{id}/neighborhood")
    public ResponseEntity<PetNeighborhoodDto> neighborhood(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(petService.getNeighborhood(id, authentication));
    }

    @GetMapping("/{id}/walk-distance/week")
    public ResponseEntity<List<PetWalkDistanceDayDto>> weeklyWalkDistance(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate weekStart,
            Authentication authentication) {
        return ResponseEntity.ok(petService.getWeeklyWalkDistance(id, weekStart, authentication));
    }

    @PostMapping("/{id}/locations")
    public ResponseEntity<LocationDTO> addLocation(
            @PathVariable Long id,
            @RequestBody LocationRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(petService.addLocation(id, request, authentication));
    }
}
