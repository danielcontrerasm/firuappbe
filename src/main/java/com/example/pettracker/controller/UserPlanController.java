package com.example.pettracker.controller;

import com.example.pettracker.entity.User;
import com.example.pettracker.service.CurrentUserService;
import com.example.pettracker.service.UserService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserPlanController {
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserPlanController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @PutMapping("/{userId}/plan")
    public Map<String, Object> updatePlan(
            @PathVariable Long userId,
            @RequestBody PlanRequest request) {
        User updated = userService.updatePlan(userId, request == null ? null : request.plan());
        currentUserService.evict(updated.getEmail());
        return Map.of("userId", updated.getId(), "plan", updated.getPlan());
    }

    public record PlanRequest(User.Plan plan) {
    }
}
