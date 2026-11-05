package com.example.pettracker.gps;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Device clock zones are independent of the application's storage/display zone. */
@Component
@ConfigurationProperties(prefix = "gps.jt808")
public class Jt808TimeZones {
    private Map<String, String> terminalTimeZones = new HashMap<>();

    public Map<String, String> getTerminalTimeZones() {
        return terminalTimeZones;
    }

    public void setTerminalTimeZones(Map<String, String> terminalTimeZones) {
        this.terminalTimeZones = new HashMap<>(terminalTimeZones);
    }

    @PostConstruct
    void validateTimeZones() {
        terminalTimeZones.values().forEach(ZoneId::of);
    }

    public ZoneId resolve(String terminalId, ZoneId fallback) {
        String configured = terminalTimeZones.get(terminalId);
        return configured == null ? fallback : ZoneId.of(configured);
    }
}
