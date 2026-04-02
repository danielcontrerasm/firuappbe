package com.example.pettracker.service;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.entity.Geofence;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.PetRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTReader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class GeofencingService {

    private final GeofenceRepository geofenceRepository;
    private final PetRepository petRepository;
    private final NotificationService notificationService;
    private final AlertService alertService;
    private final VolunteerNotificationService volunteerNotificationService;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public GeofencingService(GeofenceRepository geofenceRepository,
                             PetRepository petRepository,
                             NotificationService notificationService,
                             AlertService alertService,
                             VolunteerNotificationService volunteerNotificationService
    ) {
        this.geofenceRepository = geofenceRepository;
        this.petRepository = petRepository;
        this.notificationService = notificationService;
        this.alertService = alertService;
        this.volunteerNotificationService = volunteerNotificationService;
    }

    public boolean isOutside(Location location) {
        Geofence g = geofenceRepository.findByPetId(location.getPet().getId()).orElse(null);
        if (g == null) return false;

        // Circle check
        if (g.getType() == Geofence.Type.CIRCLE && g.getCenterLat()!=null && g.getCenterLng()!=null && g.getRadiusMeters()!=null) {
            double dist = distanceMeters(g.getCenterLat(), g.getCenterLng(), location.getLatitude(), location.getLongitude());
            return dist > g.getRadiusMeters();
        }

        // Polygon check: check if POINT is inside polygon
        if (g.getType() == Geofence.Type.POLYGON && g.getPolygon() != null) {
            Point pt = geometryFactory.createPoint(new Coordinate(location.getLongitude(), location.getLatitude()));
            return !g.getPolygon().contains(pt);
        }

        return false;
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
        try {
            if (isOutside(location)) {
                Pet pet = petRepository.findById(location.getPet().getId()).orElse(null);
                if (pet == null) return;
                // create alert
                String metadata = String.format("Out of geofence at %f,%f", location.getLatitude(), location.getLongitude());
                alertService.createAlert(pet, com.example.pettracker.entity.Alert.AlertType.BOUNDARY, metadata);
                // notify owner
                notificationService.notifyOwner(pet.getOwner(),
                        "Alert: Your pet " + pet.getName() + " left its geofence. " + metadata);

            }
        } catch (Exception e) {
            e.printStackTrace();
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
    @Async("geofenceExecutor")
    public void checkGeofence(Location update) {
        // Compute distance from zone center

        if (isOutside(update)) {
            volunteerNotificationService.notifyVolunteers(update.getPet(), "OUT_OF_ZONE");

        }
       /* double distance = getDistance(update);
        if (distance > update.getZoneRadius()) {
            volunteerNotificationService.notifyVolunteers(update.getPet(), "OUT_OF_ZONE");
        }*/
    }

    private static Object getDistance(Location update) {
       /* double distance = GeoUtils.distance(update.getLat(), update.getLon(),
                update.getZoneLat(), update.getZoneLon());
        return distance;*/
        return null;
    }

}
