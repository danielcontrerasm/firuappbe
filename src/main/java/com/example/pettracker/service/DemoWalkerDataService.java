package com.example.pettracker.service;

import com.example.pettracker.entity.DogWalkerProfile;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.DogWalkerProfileRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates stable local accounts so the owner -> walker request flow can be tested
 * without first going through the approval workflow. Set DEMO_WALKERS_ENABLED=false
 * when these accounts are not wanted in a deployment.
 */
@Service
@ConditionalOnProperty(name = "demo.walkers.enabled", havingValue = "true", matchIfMissing = true)
public class DemoWalkerDataService {

    public static final String DEMO_PASSWORD = "FiruDemo123!";
    public static final String DEMO_OWNER_EMAIL = "owner.demo@firuapp.local";

    private final UserRepository userRepository;
    private final DogWalkerProfileRepository walkerProfileRepository;
    private final PetRepository petRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoWalkerDataService(
            UserRepository userRepository,
            DogWalkerProfileRepository walkerProfileRepository,
            PetRepository petRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.petRepository = petRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDemoAccounts() {
        User owner = findOrCreateOwner();
        seedOwnerPet(owner);

        List<DemoWalker> walkers = List.of(
                new DemoWalker(
                        "Camila Torres",
                        "camila.walker@firuapp.local",
                        "Alto Prado",
                        "Paseos tranquilos y juegos de olfato para perros pequeños y medianos.",
                        5,
                        "45000",
                        "Paseos, visitas y administración de medicamentos",
                        "Lun–Vie · 7:00 a. m. – 6:00 p. m."),
                new DemoWalker(
                        "Mateo Ríos",
                        "mateo.walker@firuapp.local",
                        "Riomar",
                        "Experiencia con perros activos, rutas largas y entrenamiento básico.",
                        7,
                        "52000",
                        "Paseos largos, running y entrenamiento básico",
                        "Todos los días · 6:00 a. m. – 8:00 p. m."),
                new DemoWalker(
                        "Sofía Herrera",
                        "sofia.walker@firuapp.local",
                        "El Prado",
                        "Cuidado cercano y reportes con fotos durante cada paseo.",
                        4,
                        "40000",
                        "Paseos, cuidado por horas y fotos",
                        "Mar–Dom · 8:00 a. m. – 5:00 p. m.")
        );

        walkers.forEach(this::findOrCreateWalker);
    }

    private User findOrCreateOwner() {
        return userRepository.findByEmail(DEMO_OWNER_EMAIL).orElseGet(() -> userRepository.save(User.builder()
                .name("Dueño Demo")
                .email(DEMO_OWNER_EMAIL)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .phone("3000000000")
                .role(User.Role.USER)
                .build()));
    }

    private void seedOwnerPet(User owner) {
        if (petRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Luna").isPresent()) {
            return;
        }
        petRepository.save(Pet.builder()
                .name("Luna")
                .type("Dog")
                .race("Golden retriever")
                .age(3)
                .weight(22.0)
                .owner(owner)
                .status(Pet.Status.ACTIVE)
                .createdAt(Instant.now())
                .build());
    }

    private void findOrCreateWalker(DemoWalker demoWalker) {
        User user = userRepository.findByEmail(demoWalker.email()).orElseGet(() -> userRepository.save(User.builder()
                .name(demoWalker.name())
                .email(demoWalker.email())
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .phone("3000000000")
                .role(User.Role.WALKER)
                .build()));

        if (walkerProfileRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        walkerProfileRepository.save(DogWalkerProfile.builder()
                .user(user)
                .bio(demoWalker.bio())
                .neighborhood(demoWalker.neighborhood())
                .experienceYears(demoWalker.experienceYears())
                .basePrice(new BigDecimal(demoWalker.basePrice()))
                .priceNotes("Tarifa demo para pruebas")
                .services(demoWalker.services())
                .availability(demoWalker.availability())
                .active(Boolean.TRUE)
                .approvalStatus(DogWalkerProfile.ApprovalStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private record DemoWalker(
            String name,
            String email,
            String neighborhood,
            String bio,
            Integer experienceYears,
            String basePrice,
            String services,
            String availability) {
    }
}
