package com.example.pettracker.controller;

import com.example.pettracker.dto.AdminMetricsDto;
import com.example.pettracker.service.AdminMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    public AdminMetricsController(AdminMetricsService adminMetricsService) {
        this.adminMetricsService = adminMetricsService;
    }

    @GetMapping
    public AdminMetricsDto getMetrics() {
        return adminMetricsService.getMetrics();
    }
}
