package com.example.pettracker.config;

import com.example.pettracker.service.CachedPetLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GpsCacheConfig {

    @Bean("gpsPetByTerminalIdCache")
    public Cache<String, CachedPetLookup> gpsPetByTerminalIdCache(
            @Value("${gps.cache.pet-by-terminal-id.maximum-size:10000}") long maximumSize) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<String, CachedPetLookup>() {
                    @Override
                    public long expireAfterCreate(String key, CachedPetLookup value, long currentTime) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(
                            String key,
                            CachedPetLookup value,
                            long currentTime,
                            long currentDuration) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(
                            String key,
                            CachedPetLookup value,
                            long currentTime,
                            long currentDuration) {
                        return currentDuration;
                    }
                })
                .recordStats()
                .build();
    }
}
