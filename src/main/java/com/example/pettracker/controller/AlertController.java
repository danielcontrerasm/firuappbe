package com.example.pettracker.controller;

import com.example.pettracker.dto.AlertDto;
import com.example.pettracker.service.AlertService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertDto> list(Authentication authentication) {
        return alertService.getOpenAlertsForCurrentUser(authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(@PathVariable Long id, Authentication authentication) {
        alertService.dismissForCurrentUser(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
