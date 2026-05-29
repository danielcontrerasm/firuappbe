package com.example.pettracker.controller;

import com.example.pettracker.dto.AuthDTOs.LoginRequest;
import com.example.pettracker.dto.AuthDTOs.RegisterRequest;
import com.example.pettracker.dto.AuthDTOs.TokenResponse;
import com.example.pettracker.entity.User;
import com.example.pettracker.security.JwtProvider;
import com.example.pettracker.service.LoginDemoDataService;
import com.example.pettracker.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginDemoDataService loginDemoDataService;

    public AuthController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            LoginDemoDataService loginDemoDataService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.loginDemoDataService = loginDemoDataService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        userService.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new RuntimeException("User already exists with email: " + request.getEmail());
        });

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
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
        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        try {
            loginDemoDataService.seedForLogin(user);
        } catch (RuntimeException e) {
            log.warn("Login demo data seeding failed for user {}", user.getEmail(), e);
        }

        return new TokenResponse(jwtProvider.generateToken(user.getEmail()));
    }
}
