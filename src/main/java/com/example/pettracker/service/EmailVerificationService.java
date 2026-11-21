package com.example.pettracker.service;

import com.example.pettracker.entity.EmailVerificationToken;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.EmailVerificationTokenRepository;
import com.example.pettracker.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class EmailVerificationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final String verificationBaseUrl;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            @Value("${app.verification-base-url:http://localhost:8080}") String verificationBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Transactional
    public boolean sendVerificationEmail(User user) {
        tokenRepository.deleteByUserId(user.getId());
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user);
        verificationToken.setTokenHash(hash(token));
        verificationToken.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(verificationToken);

        String link = UriComponentsBuilder.fromUriString(verificationBaseUrl)
                .path("/api/auth/verify-email")
                .queryParam("token", token)
                .build()
                .toUriString();
        return notificationService.sendEmail(
                user.getEmail(),
                "Confirm your FiruApp email",
                "Hi " + (user.getName() == null ? "there" : user.getName())
                        + ",\n\nConfirm your email by opening this link within 24 hours:\n" + link
                        + "\n\nIf you did not create this account, you can ignore this email.");
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification link is invalid or expired");
        }
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or expired"));
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            tokenRepository.delete(token);
            throw new IllegalArgumentException("Verification link is invalid or expired");
        }
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.delete(token);
    }

    private String hash(String value) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
