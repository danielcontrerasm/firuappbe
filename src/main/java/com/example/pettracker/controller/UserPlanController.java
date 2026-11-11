package com.example.pettracker.controller;

import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.service.CurrentUserService;
import com.example.pettracker.service.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserPlanController {
    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final PetRepository petRepository;

    public UserPlanController(
            UserService userService,
            CurrentUserService currentUserService,
            PetRepository petRepository) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.petRepository = petRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : userService.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("name", user.getName());
            row.put("email", user.getEmail());
            row.put("phone", user.getPhone());
            row.put("role", user.getRole());
            row.put("plan", user.getPlan());
            row.put("petsCount", user.getId() == null ? 0L : petRepository.countByOwnerId(user.getId()));
            result.add(row);
        }
        return result;
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
