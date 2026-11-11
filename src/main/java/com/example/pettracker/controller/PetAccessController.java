package com.example.pettracker.controller;

import com.example.pettracker.dto.PetShareDtos.ShareRequest;
import com.example.pettracker.dto.PetShareDtos.SharedPerson;
import com.example.pettracker.service.PetAccessService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pets/{petId}/shares")
public class PetAccessController {
    private final PetAccessService petAccessService;

    public PetAccessController(PetAccessService petAccessService) {
        this.petAccessService = petAccessService;
    }

    @PostMapping
    public ResponseEntity<SharedPerson> share(
            @PathVariable Long petId,
            @RequestBody ShareRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(petAccessService.share(petId, request, authentication));
    }

    @GetMapping
    public List<SharedPerson> list(@PathVariable Long petId, Authentication authentication) {
        return petAccessService.list(petId, authentication);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(
            @PathVariable Long petId,
            @PathVariable Long userId,
            Authentication authentication) {
        petAccessService.revoke(petId, userId, authentication);
        return ResponseEntity.noContent().build();
    }
}
