package com.example.pettracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LocationServiceTest {

    private final LocationService locationService = new LocationService(
            null,
            null,
            null,
            null,
            new AppTimeService("America/Bogota")
    );

    @Test
    void parsesUtcTimestampInAppTimezone() {
        LocalDateTime timestamp = locationService.parseRequestTimestamp("2026-10-06T12:00:00Z");

        assertThat(timestamp).isEqualTo(LocalDateTime.of(2026, 10, 6, 7, 0));
    }

    @Test
    void parsesLocalTimestampWithoutTimezone() {
        LocalDateTime timestamp = locationService.parseRequestTimestamp("2026-10-06T12:00:00");

        assertThat(timestamp).isEqualTo(LocalDateTime.of(2026, 10, 6, 12, 0));
    }

    @Test
    void rejectsInvalidTimestamp() {
        assertThatThrownBy(() -> locationService.parseRequestTimestamp("not-a-timestamp"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
