package com.example.pettracker.config;

import com.example.pettracker.model.Geofence;
import com.example.pettracker.model.Pet;
import com.example.pettracker.model.PetLocation;
import com.example.pettracker.model.Role;
import com.example.pettracker.model.User;
import com.example.pettracker.repository.GeofenceRepository;
import com.example.pettracker.repository.PetLocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            PetRepository petRepository,
            PetLocationRepository petLocationRepository,
            GeofenceRepository geofenceRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            User admin = new User();
            admin.setFullName("System Admin");
            admin.setEmail("admin@pettracker.local");
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRoles(Set.of(Role.ADMIN));
            admin = userRepository.save(admin);

            User owner = new User();
            owner.setFullName("Olivia Owner");
            owner.setEmail("owner@pettracker.local");
            owner.setUsername("owner");
            owner.setPassword(passwordEncoder.encode("owner123"));
            owner.setRoles(Set.of(Role.OWNER));
            owner = userRepository.save(owner);

            User volunteer = new User();
            volunteer.setFullName("Victor Volunteer");
            volunteer.setEmail("volunteer@pettracker.local");
            volunteer.setUsername("volunteer");
            volunteer.setPassword(passwordEncoder.encode("volunteer123"));
            volunteer.setRoles(Set.of(Role.VOLUNTEER));
            volunteer = userRepository.save(volunteer);

            Pet pet = new Pet();
            pet.setName("Luna");
            pet.setSpecies("Dog");
            pet.setBreed("Border Collie");
            pet.setDescription("GPS collar enabled");
            pet.setOwner(owner);
            pet.setLost(false);
            pet = petRepository.save(pet);

            PetLocation location = new PetLocation();
            location.setPet(pet);
            location.setLatitude(4.7110);
            location.setLongitude(-74.0721);
            location.setSpeed(0.5);
            location.setAccuracyMeters(8.0);
            location.setRecordedAt(Instant.now());
            petLocationRepository.save(location);

            Geofence geofence = new Geofence();
            geofence.setPet(pet);
            geofence.setCenterLatitude(4.7110);
            geofence.setCenterLongitude(-74.0721);
            geofence.setRadiusMeters(500);
            geofenceRepository.save(geofence);
        };
    }
}
