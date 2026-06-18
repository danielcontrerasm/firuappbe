package com.example.pettracker.transit.service;

import com.example.pettracker.entity.User;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.service.NotificationService;
import com.example.pettracker.transit.dto.TransitScheduledGeofenceRequest;
import com.example.pettracker.transit.entity.TransitGeofenceEvent;
import com.example.pettracker.transit.entity.TransitLocation;
import com.example.pettracker.transit.entity.TransitScheduledGeofence;
import com.example.pettracker.transit.entity.TransitScheduledGeofence.ShapeType;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.repository.TransitGeofenceEventRepository;
import com.example.pettracker.transit.repository.TransitLocationRepository;
import com.example.pettracker.transit.repository.TransitScheduledGeofenceRepository;
import com.example.pettracker.transit.repository.TransitSearchGroupRepository;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransitGeofencingService {

    private final TransitScheduledGeofenceRepository transitScheduledGeofenceRepository;
    private final TransitLocationRepository transitLocationRepository;
    private final TransitGeofenceEventRepository transitGeofenceEventRepository;
    private final TransitSearchGroupRepository transitSearchGroupRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public TransitGeofencingService(
            TransitScheduledGeofenceRepository transitScheduledGeofenceRepository,
            TransitLocationRepository transitLocationRepository,
            TransitGeofenceEventRepository transitGeofenceEventRepository,
            TransitSearchGroupRepository transitSearchGroupRepository,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.transitScheduledGeofenceRepository = transitScheduledGeofenceRepository;
        this.transitLocationRepository = transitLocationRepository;
        this.transitGeofenceEventRepository = transitGeofenceEventRepository;
        this.transitSearchGroupRepository = transitSearchGroupRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    public TransitScheduledGeofence createGeofence(TransitVehicle vehicle, TransitScheduledGeofenceRequest request) {
        TransitScheduledGeofence geofence = TransitScheduledGeofence.builder()
                .vehicle(vehicle)
                .name(request.name())
                .zoneType(TransitScheduledGeofence.ZoneType.valueOf(normalizeEnum(request.zoneType())))
                .shapeType(TransitScheduledGeofence.ShapeType.valueOf(normalizeEnum(request.shapeType())))
                .centerLat(request.centerLat())
                .centerLng(request.centerLng())
                .radiusMeters(request.radiusMeters())
                .polygon(buildPolygon(request.coordinates()))
                .activeFrom(parseTime(request.activeFrom()))
                .activeTo(parseTime(request.activeTo()))
                .activeDays(normalizeDays(request.activeDays()))
                .enabled(request.enabled() == null ? Boolean.TRUE : request.enabled())
                .build();
        validate(geofence);
        return transitScheduledGeofenceRepository.save(geofence);
    }

    public List<TransitScheduledGeofence> getVehicleGeofences(Long vehicleId) {
        return transitScheduledGeofenceRepository.findByVehicleId(vehicleId);
    }

    public TransitScheduledGeofence findById(Long id) {
        return transitScheduledGeofenceRepository.findById(id).orElse(null);
    }

    public void deleteGeofence(Long geofenceId) {
        transitScheduledGeofenceRepository.deleteById(geofenceId);
    }

    @Async("geofenceExecutor")
    @Transactional
    public void auditLocation(Long locationId) {
        TransitLocation location = transitLocationRepository.findById(locationId).orElse(null);
        if (location == null || location.getVehicle() == null) {
            return;
        }

        List<TransitScheduledGeofence> activeGeofences = transitScheduledGeofenceRepository
                .findByVehicleIdAndEnabledTrue(location.getVehicle().getId())
                .stream()
                .filter(geofence -> appliesTo(location.getTimestamp(), geofence))
                .toList();

        for (TransitScheduledGeofence geofence : activeGeofences) {
            if (isOutside(location, geofence)) {
                TransitGeofenceEvent event = transitGeofenceEventRepository.save(TransitGeofenceEvent.builder()
                        .vehicle(location.getVehicle())
                        .geofence(geofence)
                        .location(location)
                        .zoneType(geofence.getZoneType())
                        .eventType(TransitGeofenceEvent.EventType.OUTSIDE_ACTIVE_GEOFENCE)
                        .message(buildMessage(location, geofence))
                        .build());
                notifyStakeholders(event);
            }
        }
    }

    private void notifyStakeholders(TransitGeofenceEvent event) {
        TransitVehicle vehicle = event.getVehicle();
        User owner = vehicle.getOwner();
        if (owner != null) {
            notificationService.notifyOwner(owner, event.getMessage());
        }

        transitSearchGroupRepository.findByVehicleId(vehicle.getId()).stream()
                .filter(group -> "active".equalsIgnoreCase(group.getStatus()))
                .flatMap(group -> group.getVolunteers().stream())
                .map(User::getId)
                .distinct()
                .map(userRepository::findById)
                .flatMap(java.util.Optional::stream)
                .forEach(user -> {
                    if (user.getPhone() != null && !user.getPhone().isBlank()) {
                        notificationService.sendSms(user.getPhone(), event.getMessage());
                    }
                    if (user.getEmail() != null && !user.getEmail().isBlank()) {
                        notificationService.sendEmail(user.getEmail(), "Transit search alert", event.getMessage());
                    }
                });
    }

    private boolean appliesTo(LocalDateTime timestamp, TransitScheduledGeofence geofence) {
        if (timestamp == null) {
            return false;
        }
        if (geofence.getEnabled() == null || !geofence.getEnabled()) {
            return false;
        }
        if (!matchesDay(timestamp.getDayOfWeek(), geofence.getActiveDays())) {
            return false;
        }
        return matchesTime(timestamp.toLocalTime(), geofence.getActiveFrom(), geofence.getActiveTo());
    }

    private boolean matchesDay(DayOfWeek dayOfWeek, String activeDays) {
        if (activeDays == null || activeDays.isBlank()) {
            return true;
        }
        return Arrays.stream(activeDays.split(","))
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.equals(dayOfWeek.name()));
    }

    private boolean matchesTime(LocalTime time, LocalTime activeFrom, LocalTime activeTo) {
        if (activeFrom == null || activeTo == null) {
            return true;
        }
        if (!activeFrom.isAfter(activeTo)) {
            return !time.isBefore(activeFrom) && !time.isAfter(activeTo);
        }
        return !time.isBefore(activeFrom) || !time.isAfter(activeTo);
    }

    private boolean isOutside(TransitLocation location, TransitScheduledGeofence geofence) {
        if (geofence.getShapeType() == ShapeType.CIRCLE) {
            double distance = distanceMeters(
                    geofence.getCenterLat(),
                    geofence.getCenterLng(),
                    location.getLatitude(),
                    location.getLongitude());
            return distance > geofence.getRadiusMeters();
        }
        if (geofence.getShapeType() == ShapeType.POLYGON && geofence.getPolygon() != null) {
            Point point = geometryFactory.createPoint(new Coordinate(location.getLongitude(), location.getLatitude()));
            return !geofence.getPolygon().contains(point);
        }
        return false;
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

    private String buildMessage(TransitLocation location, TransitScheduledGeofence geofence) {
        return String.format(
                "%s left scheduled %s geofence '%s' at %.6f, %.6f on %s",
                location.getVehicle().getLabel(),
                geofence.getZoneType(),
                geofence.getName(),
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp());
    }

    private Polygon buildPolygon(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return null;
        }
        Coordinate[] polygonCoordinates = new Coordinate[coordinates.size() + 1];
        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coordinate = coordinates.get(i);
            polygonCoordinates[i] = new Coordinate(coordinate.get(1), coordinate.get(0));
        }
        polygonCoordinates[coordinates.size()] = polygonCoordinates[0];
        return geometryFactory.createPolygon(polygonCoordinates);
    }

    private LocalTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    private String normalizeDays(String activeDays) {
        if (activeDays == null || activeDays.isBlank()) {
            return null;
        }
        return Arrays.stream(activeDays.split(","))
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String normalizeEnum(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enum value is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validate(TransitScheduledGeofence geofence) {
        if (geofence.getShapeType() == ShapeType.CIRCLE) {
            if (geofence.getCenterLat() == null || geofence.getCenterLng() == null || geofence.getRadiusMeters() == null) {
                throw new IllegalArgumentException("Circle geofence requires centerLat, centerLng and radiusMeters");
            }
            return;
        }
        if (geofence.getPolygon() == null) {
            throw new IllegalArgumentException("Polygon geofence requires coordinates");
        }
    }
}
