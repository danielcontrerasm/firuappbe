package com.example.pettracker.dto;

import lombok.*;

public class AuthDTOs {
    @Data public static class RegisterRequest {
        private String name;
        private String email;
        private String password;
        private String phone;
    }

    @Data public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data @AllArgsConstructor public static class TokenResponse {
        private String token;
    }
}

