package com.example.pettracker.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppTimeService {

    private final ZoneId zoneId;

    public AppTimeService(@Value("${pettracker.time-zone:America/Bogota}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }

    public LocalDateTime fromInstant(Instant instant) {
        return LocalDateTime.ofInstant(instant, zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
