package com.example.pettracker.service;

import com.example.pettracker.entity.User;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserService userService;
    private final Cache<String, CachedUserLookup> userByEmailCache;

    public CurrentUserService(
            UserService userService,
            @Qualifier("userByEmailCache") Cache<String, CachedUserLookup> userByEmailCache) {
        this.userService = userService;
        this.userByEmailCache = userByEmailCache;
    }

    public User require(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Authentication required");
        }
        String email = normalizeEmail(authentication.getName());
        CachedUserLookup cached = userByEmailCache.getIfPresent(email);
        if (cached != null) {
            return cached.toUser();
        }

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        userByEmailCache.put(email, CachedUserLookup.from(user));
        return user;
    }

    public void evict(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userByEmailCache.invalidate(normalizeEmail(email));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
