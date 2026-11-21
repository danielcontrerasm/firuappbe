package com.example.pettracker.controller;

import com.example.pettracker.dto.AuthDTOs.LoginRequest;
import com.example.pettracker.dto.AuthDTOs.RegisterRequest;
import com.example.pettracker.dto.AuthDTOs.TokenResponse;
import com.example.pettracker.entity.User;
import com.example.pettracker.security.JwtProvider;
import com.example.pettracker.service.UserService;
import com.example.pettracker.service.LoginDemoDataService;
import com.example.pettracker.service.EmailVerificationService;
import com.example.pettracker.dto.AuthDTOs.MessageResponse;
import com.example.pettracker.dto.AuthDTOs.EmailActionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginDemoDataService loginDemoDataService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            LoginDemoDataService loginDemoDataService,
            EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.loginDemoDataService = loginDemoDataService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<EmailActionResponse> register(@RequestBody RegisterRequest request) {
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
                .plan(request.getPlan() == null ? User.Plan.BASIC : request.getPlan())
                .emailVerified(false)
                .build();

        User saved = userService.save(user);
        boolean emailSent = emailVerificationService.sendVerificationEmail(saved);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new EmailActionResponse(emailSent, emailSent
                        ? "Account created. Check your inbox and spam folder for the confirmation link."
                        : "Account created, but we could not send the confirmation email. Please try Resend confirmation email before signing in."));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok(new MessageResponse("Email verified. You can now sign in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<EmailActionResponse> resendVerification(@RequestBody LoginRequest request) {
        boolean emailSent = userService.findByEmail(normalizeEmail(request.getEmail()))
                .filter(user -> !user.isEmailVerified())
                .map(emailVerificationService::sendVerificationEmail)
                .orElse(false);
        return ResponseEntity.ok(new EmailActionResponse(emailSent, emailSent
                ? "A new confirmation email has been sent. Check your inbox and spam folder."
                : "We could not send a confirmation email. Check the address and try again later."));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        User user = userService.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        if (!user.isEmailVerified()) {
            throw new RuntimeException("Please verify your email before signing in");
        }

        loginDemoDataService.seedForLogin(user);
        return new TokenResponse(jwtProvider.generateToken(user.getEmail()));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new RuntimeException("Email is required");
        }
        return email.trim().toLowerCase();
    }
}
