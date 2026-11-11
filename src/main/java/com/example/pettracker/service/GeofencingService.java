package com.example.pettracker.service;

import com.example.pettracker.dto.GeofenceRequests.CircleRequest;
import com.example.pettracker.dto.GeofenceRequests.PolygonRequest;
import com.example.pettracker.dto.GeofenceResponseDto;
import com.example.pettracker.entity.Geofence;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.GeofenceMapper;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import java.util.List;
import java.util.Locale;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class GeofencingService {

    private static final DateTimeFormatter ALERT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GeofenceRepository geofenceRepository;
    private final PetRepository petRepository;
    private final LocationRepository locationRepository;
    private final NotificationService notificationService;
    private final AlertService alertService;
    private final VolunteerNotificationService volunteerNotificationService;
    private final CurrentUserService currentUserService;
    private final GeometryFactory geometryFactory;
    private final PetAccessService petAccessService;

    public GeofencingService(GeofenceRepository geofenceRepository,
                             PetRepository petRepository,
                             LocationRepository locationRepository,
                             NotificationService notificationService,
                             AlertService alertService,
                             VolunteerNotificationService volunteerNotificationService,
                             CurrentUserService currentUserService,
                             GeometryFactory geometryFactory,
                             PetAccessService petAccessService
    ) {
        this.geofenceRepository = geofenceRepository;
        this.petRepository = petRepository;
        this.locationRepository = locationRepository;
        this.notificationService = notificationService;
        this.alertService = alertService;
        this.volunteerNotificationService = volunteerNotificationService;
        this.currentUserService = currentUserService;
        this.geometryFactory = geometryFactory;
        this.petAccessService = petAccessService;
    }

    @Transactional
    public GeofenceResponseDto createCircle(Long petId, CircleRequest request, Authentication authentication) {
        Pet pet = requireManageablePet(petId, authentication);
        enforceGeofenceLimit(pet, authentication);
        validateCircle(request);

        Geofence geofence = Geofence.builder()
                .pet(pet)
                .type(Geofence.Type.CIRCLE)
                .centerLat(request.getCenterLat())
                .centerLng(request.getCenterLng())
                .radiusMeters(request.getRadiusMeters())
                .build();

        Geofence saved = geofenceRepository.save(geofence);
        checkLatestLocationForNewGeofence(petId);
        return GeofenceMapper.toDto(saved);
    }

    @Transactional
    public GeofenceResponseDto createPolygon(Long petId, PolygonRequest request, Authentication authentication) {
        Pet pet = requireManageablePet(petId, authentication);
        enforceGeofenceLimit(pet, authentication);
        Polygon polygon = buildPolygon(request);

        Geofence geofence = Geofence.builder()
                .pet(pet)
                .type(Geofence.Type.POLYGON)
                .polygon(polygon)
                .build();

        Geofence saved = geofenceRepository.save(geofence);
        checkLatestLocationForNewGeofence(petId);
        return GeofenceMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<GeofenceResponseDto> getByPet(Long petId, Authentication authentication) {
        requireAccessiblePet(petId, authentication);
        return geofenceRepository.findAllByPetIdOrderByIdAsc(petId).stream()
                .map(GeofenceMapper::toDto)
                .toList();
    }

    @Transactional
    public void deleteByPet(Long petId, Authentication authentication) {
        requireManageablePet(petId, authentication);
        if (geofenceRepository.countByPetId(petId) == 0) {
            throw notFound("Geofence not found");
        }
        geofenceRepository.deleteByPetId(petId);
    }

    @Transactional
    public void deleteById(Long petId, Long geofenceId, Authentication authentication) {
        requireManageablePet(petId, authentication);
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .filter(candidate -> candidate.getPet() != null
                        && petId.equals(candidate.getPet().getId()))
                .orElseThrow(() -> notFound("Geofence not found"));
        geofenceRepository.delete(geofence);
    }

    private Pet requireAccessiblePet(Long petId, Authentication authentication) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> notFound("Pet not found"));
        User current = currentUserService.require(authentication);
        if (!petAccessService.canAccess(current, pet)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this pet");
        }
        return pet;
    }

    private Pet requireManageablePet(Long petId, Authentication authentication) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> notFound("Pet not found"));
        User current = currentUserService.require(authentication);
        boolean ownerOrAdmin = current.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null && current.getId().equals(pet.getOwner().getId()));
        if (!ownerOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the pet owner can manage geofences");
        }
        return pet;
    }

    private void enforceGeofenceLimit(Pet pet, Authentication authentication) {
        User current = currentUserService.require(authentication);
        long currentCount = geofenceRepository.countByPetId(pet.getId());
        int limit = PlanPolicy.geofencesPerPet(current);
        if (currentCount >= limit) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tu plan permite hasta " + limit + " zonas seguras por mascota");
        }
    }

    private void validateCircle(CircleRequest request) {
        if (request == null
                || request.getCenterLat() == null
                || request.getCenterLng() == null
                || request.getRadiusMeters() == null
                || request.getRadiusMeters() <= 0) {
            throw new IllegalArgumentException("centerLat, centerLng, and positive radiusMeters are required");
        }
    }

    private Polygon buildPolygon(PolygonRequest request) {
        List<List<Double>> coordinates = request == null ? null : request.getCoordinates();
        if (coordinates == null || coordinates.size() < 3) {
            throw new IllegalArgumentException("Polygon requires at least three coordinates");
        }

        Coordinate[] polygonCoordinates = new Coordinate[coordinates.size() + 1];
        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coordinate = coordinates.get(i);
            if (coordinate == null || coordinate.size() < 2 || coordinate.get(0) == null || coordinate.get(1) == null) {
                throw new IllegalArgumentException("Each polygon coordinate must contain latitude and longitude");
            }
            double lat = coordinate.get(0);
            double lng = coordinate.get(1);
            polygonCoordinates[i] = new Coordinate(lng, lat);
        }
        polygonCoordinates[coordinates.size()] = polygonCoordinates[0];
        return geometryFactory.createPolygon(polygonCoordinates);
    }

    private void checkLatestLocationForNewGeofence(Long petId) {
        locationRepository.findTopByPetIdOrderByTimestampDesc(petId)
                .ifPresent(this::checkGeofence);
    }

    private ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    public boolean isOutside(Location location) {
        List<Geofence> geofences = geofenceRepository.findAllByPetIdOrderByIdAsc(location.getPet().getId());
        if (geofences.isEmpty()) return false;

        Point point = geometryFactory.createPoint(new Coordinate(location.getLongitude(), location.getLatitude()));
        boolean insideAny = geofences.stream().anyMatch(g -> {
            if (g.getType() == Geofence.Type.CIRCLE
                    && g.getCenterLat() != null && g.getCenterLng() != null && g.getRadiusMeters() != null) {
                double distance = distanceMeters(g.getCenterLat(), g.getCenterLng(), location.getLatitude(), location.getLongitude());
                return distance <= g.getRadiusMeters();
            }
            return g.getType() == Geofence.Type.POLYGON
                    && g.getPolygon() != null
                    && g.getPolygon().contains(point);
        });
        return !insideAny;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    public void checkAndAlert(Location location) {
        evaluateOutsideGeofence(location);
    }

    @Async("geofenceExecutor")
    @Transactional
    public void checkGeofence(Location update) {
        evaluateOutsideGeofence(update);
    }

    private void evaluateOutsideGeofence(Location location) {
        try {
            if (location == null || location.getPet() == null || location.getPet().getId() == null) {
                return;
            }
            if (!isOutside(location)) {
                return;
            }

            Pet pet = petRepository.findById(location.getPet().getId()).orElse(null);
            if (pet == null) return;

            String eventTime = location.getTimestamp() == null
                    ? "unknown"
                    : ALERT_TIME_FORMATTER.format(location.getTimestamp());
            String metadata = String.format(Locale.ROOT,
                    "Out of geofence at %f,%f on %s",
                    location.getLatitude(), location.getLongitude(), eventTime);
            alertService.createAlert(pet, com.example.pettracker.entity.Alert.AlertType.BOUNDARY, metadata);
            notificationService.notifyOwner(pet.getOwner(),
                    "Alert: Your pet " + pet.getName() + " left its geofence. " + metadata);
            volunteerNotificationService.notifyVolunteers(pet, "OUT_OF_ZONE");
        } catch (RuntimeException ex) {
            Long petId = location == null || location.getPet() == null ? null : location.getPet().getId();
            double latitude = location == null ? 0 : location.getLatitude();
            double longitude = location == null ? 0 : location.getLongitude();
            log.warn("Failed to evaluate geofence alert petId={} lat={} lon={}: {}",
                    petId, latitude, longitude, ex.getMessage(), ex);
        }
    }

   /* public void monitorAndTriggerAlerts(Location location) {
        Long petId = location.getPet().getId();
        Pet pet = petRepository.findById(petId).orElse(null);

        if (pet == null) return;

        boolean outsideGeofence = isPetOutsideGeofence(petId, location.getLatitude(), location.getLongitude());

        if (outsideGeofence) {
            String ownerName = pet.getOwner().getName();
            String ownerEmail = pet.getOwner().getEmail();
            String ownerPhone = pet.getOwner().getPhone();

            String alertMessage = "Alert: Your pet " + pet.getName() + " has left its geofenced area!";

            // Send SMS
            if (ownerPhone != null && !ownerPhone.isEmpty()) {
                smsService.sendSms(ownerPhone, alertMessage);
            }

            // Send Email
            if (ownerEmail != null && !ownerEmail.isEmpty()) {
                emailService.sendEmail(ownerEmail, "Pet Geofence Alert", alertMessage);
            }

            System.out.println("Alert sent for pet: " + pet.getName());
        }
    }*/
    private static Object getDistance(Location update) {
       /* double distance = GeoUtils.distance(update.getLat(), update.getLon(),
                update.getZoneLat(), update.getZoneLon());
        return distance;*/
        return null;
    }

}
