package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginDemoDataService {

    private static final String DEMO_EMAIL = "danico411@gmail.com";

    private final PetRepository petRepository;
    private final LocationRepository locationRepository;

    public LoginDemoDataService(PetRepository petRepository, LocationRepository locationRepository) {
        this.petRepository = petRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public void seedForLogin(User user) {
        if (user == null || user.getEmail() == null || !DEMO_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return;
        }

        Pet peluche = findOrCreatePet(user, "Peluche", "Dog", "Poodle", 4, 6.8, "DEMO-PELUCHE-" + user.getId());
        Pet bella = findOrCreatePet(user, "Bella", "Dog", "Labrador", 3, 24.5, "DEMO-BELLA-" + user.getId());
        Pet rocky = findOrCreatePet(user, "Rocky", "Dog", "Beagle", 5, 11.2, "DEMO-ROCKY-" + user.getId());

        insertRoute(peluche, pobladoRoute(), 9);
        insertRoute(bella, bellaRoute(), 18);
        insertRoute(rocky, rockyRoute(), 18);
    }

    private Pet findOrCreatePet(User owner, String name, String type, String race, Integer age, Double weight, String imei) {
        return petRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), name)
                .map(existing -> {
                    existing.setType(type);
                    existing.setRace(race);
                    existing.setAge(age);
                    existing.setWeight(weight);
                    existing.setImei(imei);
                    return petRepository.save(existing);
                })
                .orElseGet(() -> petRepository.save(Pet.builder()
                        .name(name)
                        .type(type)
                        .race(race)
                        .age(age)
                        .weight(weight)
                        .imei(imei)
                        .owner(owner)
                        .status(Pet.Status.ACTIVE)
                        .build()));
    }

    private void insertRoute(Pet pet, List<Point> points, int minutesBetweenPoints) {
        LocalDateTime start = LocalDateTime.now().minusMinutes((long) minutesBetweenPoints * (points.size() - 1));
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            locationRepository.save(Location.builder()
                    .pet(pet)
                    .latitude(point.latitude())
                    .longitude(point.longitude())
                    .timestamp(start.plusMinutes((long) i * minutesBetweenPoints))
                    .build());
        }
    }

    private List<Point> pobladoRoute() {
        return List.of(
                new Point(6.21083, -75.57021),
                new Point(6.21159, -75.56949),
                new Point(6.21238, -75.56892),
                new Point(6.21307, -75.56821),
                new Point(6.21402, -75.56776),
                new Point(6.21494, -75.56722),
                new Point(6.21589, -75.56686),
                new Point(6.21675, -75.56641),
                new Point(6.21762, -75.56598),
                new Point(6.21841, -75.56543),
                new Point(6.21925, -75.56491),
                new Point(6.22004, -75.56432),
                new Point(6.22081, -75.56371),
                new Point(6.22158, -75.56302),
                new Point(6.22232, -75.56234),
                new Point(6.22309, -75.56178),
                new Point(6.22383, -75.56118),
                new Point(6.22462, -75.56054),
                new Point(6.22531, -75.55987),
                new Point(6.22607, -75.55921)
        );
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
