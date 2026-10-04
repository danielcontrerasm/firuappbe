package com.example.pettracker.controller;

import com.example.pettracker.dto.AuthDTOs.LoginRequest;
import com.example.pettracker.dto.AuthDTOs.RegisterRequest;
import com.example.pettracker.dto.AuthDTOs.TokenResponse;
import com.example.pettracker.entity.User;
import com.example.pettracker.security.JwtProvider;
import com.example.pettracker.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        userService.findByEmail(email).ifPresent(existing -> {
            throw new RuntimeException("User already exists with email: " + email);
        });

        User user = User.builder()
                .name(request.getName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(User.Role.USER)
                .build();

        User saved = userService.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TokenResponse(jwtProvider.generateToken(saved.getEmail())));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        User user = userService.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return new TokenResponse(jwtProvider.generateToken(user.getEmail()));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new RuntimeException("Email is required");
        }
        return email.trim().toLowerCase();
    }
}
