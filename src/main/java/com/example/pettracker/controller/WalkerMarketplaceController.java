package com.example.pettracker.controller;

import com.example.pettracker.dto.WalkerMarketplaceDtos.AdminWalkerUpsertRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.CurrentUserResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.DogWalkResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkDecisionRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkMessageRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkMessageResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkPositionRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkPositionResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkQuoteRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkRequestCreateRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkRequestResponse;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkerApplicationRequest;
import com.example.pettracker.dto.WalkerMarketplaceDtos.WalkerCardResponse;
import com.example.pettracker.service.WalkerMarketplaceService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WalkerMarketplaceController {

    private final WalkerMarketplaceService walkerMarketplaceService;

    public WalkerMarketplaceController(WalkerMarketplaceService walkerMarketplaceService) {
        this.walkerMarketplaceService = walkerMarketplaceService;
    }

    @GetMapping("/public/walkers")
    public List<WalkerCardResponse> listPublicWalkers() {
        return walkerMarketplaceService.listPublicWalkers();
    }

    @PostMapping("/walker-applications")
    public ResponseEntity<WalkerCardResponse> applyAsWalker(@RequestBody WalkerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walkerMarketplaceService.createWalkerApplication(request));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return walkerMarketplaceService.currentUser(authentication);
    }

    @GetMapping("/walkers/me")
    public WalkerCardResponse myWalkerProfile(Authentication authentication) {
        return walkerMarketplaceService.getMyWalkerProfile(authentication);
    }

    @PostMapping("/walk-requests")
    public ResponseEntity<WalkRequestResponse> createWalkRequest(
            Authentication authentication,
            @RequestBody WalkRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walkerMarketplaceService.createWalkRequest(authentication, request));
    }

    @GetMapping("/walk-requests")
    public List<WalkRequestResponse> listMyWalkRequests(Authentication authentication) {
        return walkerMarketplaceService.listMyWalkRequests(authentication);
    }

    @GetMapping("/walk-requests/{id}")
    public WalkRequestResponse getWalkRequest(Authentication authentication, @PathVariable Long id) {
        return walkerMarketplaceService.getWalkRequest(authentication, id);
    }

    @PutMapping("/walk-requests/{id}/quote")
    public WalkRequestResponse quoteWalkRequest(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody WalkQuoteRequest request) {
        return walkerMarketplaceService.quoteWalkRequest(authentication, id, request);
    }

    @PutMapping("/walk-requests/{id}/decision")
    public WalkRequestResponse decideWalkRequest(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody WalkDecisionRequest request) {
        return walkerMarketplaceService.decideWalkRequest(authentication, id, request);
    }

    @GetMapping("/walk-requests/{id}/messages")
    public List<WalkMessageResponse> listMessages(Authentication authentication, @PathVariable Long id) {
        return walkerMarketplaceService.listMessages(authentication, id);
    }

    @PostMapping("/walk-requests/{id}/messages")
    public ResponseEntity<WalkMessageResponse> addMessage(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody WalkMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walkerMarketplaceService.addMessage(authentication, id, request));
    }

    @PostMapping("/walk-requests/{id}/start")
    public DogWalkResponse startWalk(Authentication authentication, @PathVariable Long id) {
        return walkerMarketplaceService.startWalk(authentication, id);
    }

    @GetMapping("/dog-walks")
    public List<DogWalkResponse> listDogWalks(Authentication authentication) {
        return walkerMarketplaceService.listMyDogWalks(authentication);
    }

    @GetMapping("/dog-walks/{id}")
    public DogWalkResponse getDogWalk(Authentication authentication, @PathVariable Long id) {
        return walkerMarketplaceService.getDogWalk(authentication, id);
    }

    @GetMapping("/dog-walks/{id}/positions")
    public List<WalkPositionResponse> listDogWalkPositions(Authentication authentication, @PathVariable Long id) {
        return walkerMarketplaceService.listWalkPositions(authentication, id);
    }

    @PostMapping("/dog-walks/{id}/positions")
    public ResponseEntity<WalkPositionResponse> addDogWalkPosition(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody WalkPositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walkerMarketplaceService.addWalkPosition(authentication, id, request));
    }

    @PostMapping("/dog-walks/{id}/complete")
    public DogWalkResponse completeDogWalk(Authentication authentication, @PathVariable Long id) {
        return walkerMarketplaceService.completeWalk(authentication, id);
    }

    @GetMapping("/admin/walkers")
    public List<WalkerCardResponse> listAllWalkers() {
        return walkerMarketplaceService.listAllWalkers();
    }

    @PostMapping("/admin/walkers")
    public ResponseEntity<WalkerCardResponse> createWalkerByAdmin(@RequestBody AdminWalkerUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walkerMarketplaceService.createWalkerByAdmin(request));
    }

    @PutMapping("/admin/walkers/{id}")
    public WalkerCardResponse updateWalkerByAdmin(@PathVariable Long id, @RequestBody AdminWalkerUpsertRequest request) {
        return walkerMarketplaceService.updateWalkerByAdmin(id, request);
    }

    @DeleteMapping("/admin/walkers/{id}")
    public ResponseEntity<Void> deleteWalkerByAdmin(@PathVariable Long id) {
        walkerMarketplaceService.deleteWalkerByAdmin(id);
        return ResponseEntity.noContent().build();
    }
}
