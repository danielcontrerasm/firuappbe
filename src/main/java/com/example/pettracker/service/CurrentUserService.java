package com.example.pettracker.service;

import com.example.pettracker.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserService userService;

    public CurrentUserService(UserService userService) {
        this.userService = userService;
    }

    public User require(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Authentication required");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
