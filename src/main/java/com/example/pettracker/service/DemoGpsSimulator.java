package com.example.pettracker.service;

import com.example.pettracker.entity.Geofence;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Alert;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.format.DateTimeFormatter;
import org.locationtech.jts.geom.Envelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "demo.gps.enabled", havingValue = "true")
public class DemoGpsSimulator {

    private static final DateTimeFormatter ALERT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;
    private static final double DEFAULT_DEMO_RADIUS_METERS = 500.0;
    private static final double OUTSIDE_DISTANCE_MULTIPLIER = 2.0;
    private static final double OUTSIDE_POLYGON_OFFSET_DEGREES = 0.02;

    private final PetRepository petRepository;
    private final GeofenceRepository geofenceRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final AlertService alertService;
    private final DemoAccount demoAccount;
    private final AppTimeService appTimeService;
    private final Map<Long, Integer> routeIndexByPetId = new ConcurrentHashMap<>();

    public DemoGpsSimulator(
            PetRepository petRepository,
            GeofenceRepository geofenceRepository,
            UserRepository userRepository,
            LocationRepository locationRepository,
            AlertService alertService,
            DemoAccount demoAccount,
            AppTimeService appTimeService) {
        this.petRepository = petRepository;
        this.geofenceRepository = geofenceRepository;
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.alertService = alertService;
        this.demoAccount = demoAccount;
        this.appTimeService = appTimeService;
    }

    @Scheduled(
            fixedRateString = "${demo.gps.normal-interval-ms:300000}",
            initialDelayString = "${demo.gps.normal-initial-delay-ms:5000}"
    )
    @Transactional
    public void publishNormalDemoLocations() {
        publishNextRouteLocation("Bella", bellaRoute());
        publishNextRouteLocation("Peluche", pelucheRoute());
        publishNextRouteLocation("Rocky", rockyRoute());
    }

    @Scheduled(
            fixedRateString = "${demo.gps.outside-interval-ms:900000}",
            initialDelayString = "${demo.gps.outside-initial-delay-ms:30000}"
    )
    @Transactional
    public void triggerOutsideGeofenceEvent() {
        publishOutsideGeofenceLocation("Bella", bellaRoute().getFirst());
    }

    private void publishNextRouteLocation(String petName, List<Point> route) {
        findPet(petName).ifPresent(pet -> {
            int index = routeIndexByPetId.getOrDefault(pet.getId(), 0);
            Point point = route.get(index % route.size());
            routeIndexByPetId.put(pet.getId(), (index + 1) % route.size());
            publishLocation(pet, point);
        });
    }

    private void publishOutsideGeofenceLocation(String petName, Point defaultCenter) {
        findPet(petName).ifPresent(pet -> {
            Geofence geofence = findOrCreateDemoCircleGeofence(pet, defaultCenter);
            Point outside = outsidePointFor(geofence);
            publishLocation(pet, outside);
            // Demo alerts stay in this account; do not invoke real owner/volunteer notifications.
            alertService.createAlert(pet, Alert.AlertType.BOUNDARY,
                    "Demo geofence exit at " + outside.latitude() + "," + outside.longitude()
                            + " on " + ALERT_TIME_FORMATTER.format(appTimeService.now()));
        });
    }

    private Optional<Pet> findPet(String petName) {
        return userRepository.findByEmail(demoAccount.email())
                .filter(demoAccount::matches)
                .flatMap(owner -> petRepository.findByImei(demoAccount.imeiFor(owner, petName))
                        .filter(pet -> demoAccount.ownsDemoPet(owner, pet, petName)));
    }

    private Geofence findOrCreateDemoCircleGeofence(Pet pet, Point center) {
        return geofenceRepository.findAllByPetIdOrderByIdAsc(pet.getId()).stream()
                .findFirst()
                .orElseGet(() -> geofenceRepository.save(Geofence.builder()
                        .pet(pet)
                        .type(Geofence.Type.CIRCLE)
                        .centerLat(center.latitude())
                        .centerLng(center.longitude())
                        .radiusMeters(DEFAULT_DEMO_RADIUS_METERS)
                        .build()));
    }

    private Point outsidePointFor(Geofence geofence) {
        if (geofence.getType() == Geofence.Type.CIRCLE) {
            double radiusMeters = geofence.getRadiusMeters() == null
                    ? DEFAULT_DEMO_RADIUS_METERS
                    : geofence.getRadiusMeters();
            double latOffset = (radiusMeters * OUTSIDE_DISTANCE_MULTIPLIER) / METERS_PER_DEGREE_LATITUDE;
            return new Point(geofence.getCenterLat() + latOffset, geofence.getCenterLng());
        }

        if (geofence.getPolygon() == null) {
            return new Point(bellaRoute().getFirst().latitude() + OUTSIDE_POLYGON_OFFSET_DEGREES,
                    bellaRoute().getFirst().longitude() + OUTSIDE_POLYGON_OFFSET_DEGREES);
        }

        Envelope envelope = geofence.getPolygon().getEnvelopeInternal();
        return new Point(
                envelope.getMaxY() + OUTSIDE_POLYGON_OFFSET_DEGREES,
                envelope.getMaxX() + OUTSIDE_POLYGON_OFFSET_DEGREES
        );
    }

    private void publishLocation(Pet pet, Point point) {
        locationRepository.save(Location.builder()
                .pet(pet)
                .latitude(point.latitude())
                .longitude(point.longitude())
                .timestamp(appTimeService.now())
                .build());
    }

    private List<Point> bellaRoute() {
        return List.of(
                new Point(6.20892, -75.57211),
                new Point(6.20937, -75.57158),
                new Point(6.20988, -75.57104),
                new Point(6.21043, -75.57049),
                new Point(6.21095, -75.56998),
                new Point(6.21147, -75.56951),
                new Point(6.21203, -75.56899)
        );
    }

    private List<Point> pelucheRoute() {
        return List.of(
                new Point(6.21083, -75.57021),
                new Point(6.21159, -75.56949),
                new Point(6.21238, -75.56892),
                new Point(6.21307, -75.56821),
                new Point(6.21402, -75.56776),
                new Point(6.21494, -75.56722),
                new Point(6.21589, -75.56686)
        );
    }

    private List<Point> rockyRoute() {
        return List.of(
                new Point(6.20291, -75.57468),
                new Point(6.20344, -75.57403),
                new Point(6.20401, -75.57339),
                new Point(6.20466, -75.57282),
                new Point(6.20531, -75.57226),
                new Point(6.20595, -75.57173),
                new Point(6.20658, -75.57115)
        );
    }

    private record Point(double latitude, double longitude) {
    }
}
