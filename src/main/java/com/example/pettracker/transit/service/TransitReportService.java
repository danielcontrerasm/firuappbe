package com.example.pettracker.transit.service;

import com.example.pettracker.transit.dto.TransitReportDto;
import com.example.pettracker.transit.entity.TransitGeofenceEvent;
import com.example.pettracker.transit.entity.TransitLocation;
import com.example.pettracker.transit.entity.TransitScheduledGeofence;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.repository.TransitGeofenceEventRepository;
import com.example.pettracker.transit.repository.TransitLocationRepository;
import com.example.pettracker.transit.repository.TransitSearchGroupRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransitReportService {

    private final TransitLocationRepository transitLocationRepository;
    private final TransitGeofenceEventRepository transitGeofenceEventRepository;
    private final TransitSearchGroupRepository transitSearchGroupRepository;

    public TransitReportService(
            TransitLocationRepository transitLocationRepository,
            TransitGeofenceEventRepository transitGeofenceEventRepository,
            TransitSearchGroupRepository transitSearchGroupRepository) {
        this.transitLocationRepository = transitLocationRepository;
        this.transitGeofenceEventRepository = transitGeofenceEventRepository;
        this.transitSearchGroupRepository = transitSearchGroupRepository;
    }

    public TransitReportDto buildReport(TransitVehicle vehicle, String window) {
        String normalizedWindow = window == null ? "DAY" : window.trim().toUpperCase();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = switch (normalizedWindow) {
            case "WEEK" -> to.minusWeeks(1);
            default -> to.minusDays(1);
        };

        List<TransitLocation> locations = transitLocationRepository
                .findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(vehicle.getId(), from, to);
        List<TransitGeofenceEvent> events = transitGeofenceEventRepository.findByVehicleIdAndCreatedAtBetween(
                vehicle.getId(),
                from.atZone(ZoneId.systemDefault()).toInstant(),
                to.atZone(ZoneId.systemDefault()).toInstant());

        long outsideHomeEvents = events.stream()
                .filter(event -> event.getZoneType() == TransitScheduledGeofence.ZoneType.HOME)
                .count();
        long outsideWorkEvents = events.stream()
                .filter(event -> event.getZoneType() == TransitScheduledGeofence.ZoneType.WORK)
                .count();

        return new TransitReportDto(
                vehicle.getId(),
                vehicle.getLabel(),
                normalizedWindow,
                from,
                to,
                Instant.now(),
                locations.size(),
                totalDistanceKm(locations),
                outsideHomeEvents,
                outsideWorkEvents,
                transitSearchGroupRepository.countByVehicleIdAndStatusIgnoreCase(vehicle.getId(), "active"),
                locations.isEmpty() ? null : locations.get(locations.size() - 1).getTimestamp());
    }

    private double totalDistanceKm(List<TransitLocation> locations) {
        double totalMeters = 0;
        for (int i = 1; i < locations.size(); i++) {
            TransitLocation previous = locations.get(i - 1);
            TransitLocation current = locations.get(i);
            totalMeters += distanceMeters(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    current.getLatitude(),
                    current.getLongitude());
        }
        return Math.round((totalMeters / 1000.0) * 100.0) / 100.0;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusMeters = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }
}
