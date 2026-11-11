package com.example.pettracker.controller;

import com.example.pettracker.dto.TemporaryPetAccessDtos.AcceptResponse;
import com.example.pettracker.dto.TemporaryPetAccessDtos.CreateRequest;
import com.example.pettracker.dto.TemporaryPetAccessDtos.Response;
import com.example.pettracker.service.TemporaryPetAccessService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class TemporaryPetAccessController {
    private final TemporaryPetAccessService temporaryPetAccessService;

    public TemporaryPetAccessController(TemporaryPetAccessService temporaryPetAccessService) {
        this.temporaryPetAccessService = temporaryPetAccessService;
    }

    @PostMapping("/api/pets/{petId}/temporary-access")
    public ResponseEntity<Response> create(
            @PathVariable Long petId,
            @RequestBody CreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(temporaryPetAccessService.create(petId, request, authentication));
    }

    @GetMapping("/api/pets/{petId}/temporary-access")
    public List<Response> list(@PathVariable Long petId, Authentication authentication) {
        return temporaryPetAccessService.list(petId, authentication);
    }

    @DeleteMapping("/api/pets/{petId}/temporary-access/{accessId}")
    public ResponseEntity<Void> revoke(
            @PathVariable Long petId,
            @PathVariable Long accessId,
            Authentication authentication) {
        temporaryPetAccessService.revoke(petId, accessId, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/temporary-access/accept")
    public AcceptResponse accept(@RequestParam String token) {
        return temporaryPetAccessService.accept(token);
    }

    @PostMapping("/api/temporary-access/accept")
    public AcceptResponse acceptPost(@RequestParam String token) {
        return temporaryPetAccessService.accept(token);
    }
}
