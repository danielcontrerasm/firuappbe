package com.example.pettracker.config;

import com.example.pettracker.service.CachedPetAccessLookup;
import com.example.pettracker.service.CachedUserLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppCacheConfig {

    @Bean("userByEmailCache")
    public Cache<String, CachedUserLookup> userByEmailCache(
            @Value("${app.cache.user-by-email.maximum-size:5000}") long maximumSize,
            @Value("${app.cache.user-by-email.ttl:PT5M}") Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }

    @Bean("petAccessByIdCache")
    public Cache<Long, CachedPetAccessLookup> petAccessByIdCache(
            @Value("${app.cache.pet-access-by-id.maximum-size:10000}") long maximumSize,
            @Value("${app.cache.pet-access-by-id.ttl:PT10M}") Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }
}
