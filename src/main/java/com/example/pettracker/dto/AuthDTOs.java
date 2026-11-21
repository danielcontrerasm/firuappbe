package com.example.pettracker.dto;

import lombok.*;
import com.example.pettracker.entity.User;

public class AuthDTOs {
    @Data public static class RegisterRequest {
        private String name;
        private String email;
        private String password;
        private String phone;
        // Kept for display/forward compatibility; plan upgrades are assigned by an administrator.
        private User.Plan plan;
    }

    @Data public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data @AllArgsConstructor public static class TokenResponse {
        private String token;
    }

    @Data @AllArgsConstructor public static class MessageResponse {
        private String message;
    }

    @Data @AllArgsConstructor public static class EmailActionResponse {
        private boolean emailSent;
        private String message;
    }
}

