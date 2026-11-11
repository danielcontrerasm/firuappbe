package com.example.pettracker.service;

import com.example.pettracker.dto.TemporaryPetAccessDtos.AcceptResponse;
import com.example.pettracker.dto.TemporaryPetAccessDtos.CreateRequest;
import com.example.pettracker.dto.TemporaryPetAccessDtos.Response;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.TemporaryPetAccess;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.TemporaryPetAccessRepository;
import com.example.pettracker.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TemporaryPetAccessService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TemporaryPetAccessRepository accessRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AppTimeService appTimeService;

    public TemporaryPetAccessService(
            TemporaryPetAccessRepository accessRepository,
            PetRepository petRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            AppTimeService appTimeService) {
        this.accessRepository = accessRepository;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.appTimeService = appTimeService;
    }

    public Response create(Long petId, CreateRequest request, Authentication authentication) {
        User owner = currentUserService.require(authentication);
        Pet pet = requirePet(petId);
        requireOwnerOrAdmin(owner, pet);
        validateRequest(owner, request);

        boolean newUser = false;
        String temporaryPassword = null;
        User guest = resolveUser(request);
        if (guest == null) {
            String email = normalizeEmail(request.email());
            temporaryPassword = generateTemporaryPassword();
            guest = userRepository.save(User.builder()
                    .name(email.substring(0, email.indexOf('@')))
                    .email(email)
                    .password(passwordEncoder.encode(temporaryPassword))
                    .role(User.Role.USER)
                    .plan(User.Plan.BASIC)
                    .build());
            newUser = true;
        }
        if (pet.getOwner() != null && guest.getId().equals(pet.getOwner().getId())) {
            throw new IllegalArgumentException("El dueño ya tiene acceso a la mascota");
        }

        String invitationToken = generateToken();
        TemporaryPetAccess access = accessRepository.save(TemporaryPetAccess.builder()
                .pet(pet)
                .user(guest)
                .startsAt(request.startsAt())
                .expiresAt(request.expiresAt())
                .recurring(Boolean.TRUE.equals(request.recurring()))
                .recurringDays(String.join(",", normalizeDays(request.recurringDays())))
                .recurringStartTime(request.recurringStartTime())
                .recurringEndTime(request.recurringEndTime())
                .canViewLocation(request.canViewLocation() == null || request.canViewLocation())
                .canViewAlerts(request.canViewAlerts() == null || request.canViewAlerts())
                .canViewHistory(request.canViewHistory() == null || request.canViewHistory())
                .active(true)
                .invitationTokenHash(hash(invitationToken))
                .createdAt(Instant.now())
                .build());

        String invitationUrl = "/api/temporary-access/accept?token=" + invitationToken;
        String passwordLine = newUser
                ? "Usuario: " + guest.getEmail() + "\nContraseña temporal: " + temporaryPassword + "\n"
                : "Usa tus credenciales actuales para entrar.\n";
        notificationService.sendEmail(
                guest.getEmail(),
                "Acceso temporal a la mascota " + pet.getName(),
                "Se te ha concedido acceso temporal a la mascota " + pet.getName() + ".\n\n"
                        + passwordLine
                        + "Acepta el acceso aquí: " + invitationUrl + "\n"
                        + "Vigencia: " + request.startsAt() + " hasta " + request.expiresAt() + "\n"
                        + "Permisos: ubicación=" + access.getCanViewLocation()
                        + ", alertas=" + access.getCanViewAlerts()
                        + ", historial=" + access.getCanViewHistory());
        return toDto(access, appTimeService.nowInstant());
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long petId, Authentication authentication) {
        User owner = currentUserService.require(authentication);
        Pet pet = requirePet(petId);
        requireOwnerOrAdmin(owner, pet);
        Instant now = appTimeService.nowInstant();
        return accessRepository.findByPetIdOrderByStartsAtAsc(petId).stream()
                .map(access -> toDto(access, now))
                .toList();
    }

    public void revoke(Long petId, Long accessId, Authentication authentication) {
        User owner = currentUserService.require(authentication);
        Pet pet = requirePet(petId);
        requireOwnerOrAdmin(owner, pet);
        TemporaryPetAccess access = accessRepository.findByIdAndPetId(accessId, petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temporary access not found"));
        access.setActive(false);
        access.setRevokedAt(Instant.now());
        accessRepository.save(access);
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireAccesses() {
        Instant now = appTimeService.nowInstant();
        accessRepository.findByExpiresAtBeforeAndActiveTrue(now).forEach(access -> {
            access.setActive(false);
            access.setRevokedAt(now);
        });
    }

    public AcceptResponse accept(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is required");
        }
        TemporaryPetAccess access = accessRepository.findByInvitationTokenHash(hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found or expired"));
        if (!Boolean.TRUE.equals(access.getActive()) || Instant.now().isAfter(access.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation expired");
        }
        access.setAcceptedAt(Instant.now());
        access.setInvitationTokenHash(null);
        accessRepository.save(access);
        return new AcceptResponse("Acceso temporal aceptado", access.getId(), access.getUser().getEmail());
    }

    @Transactional(readOnly = true)
    public boolean canAccess(User user, Pet pet) {
        return findActive(user, pet).stream().anyMatch(this::isCurrentlyActive);
    }

    @Transactional(readOnly = true)
    public boolean canViewLocation(User user, Pet pet) {
        return findActive(user, pet).stream()
                .filter(this::isCurrentlyActive)
                .anyMatch(access -> Boolean.TRUE.equals(access.getCanViewLocation()));
    }

    @Transactional(readOnly = true)
    public boolean canViewHistory(User user, Pet pet) {
        return findActive(user, pet).stream()
                .filter(this::isCurrentlyActive)
                .anyMatch(access -> Boolean.TRUE.equals(access.getCanViewHistory()));
    }

    @Transactional(readOnly = true)
    public List<Long> activeAlertPetIds(User user) {
        return accessRepository.findByUserId(user.getId()).stream()
                .map(TemporaryPetAccess::getPet)
                .distinct()
                .filter(pet -> findActive(user, pet).stream()
                        .filter(this::isCurrentlyActive)
                        .anyMatch(access -> Boolean.TRUE.equals(access.getCanViewAlerts())))
                .map(Pet::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Pet> currentlyAccessiblePets(User user) {
        if (user == null || user.getId() == null) return List.of();
        return accessRepository.findByUserId(user.getId()).stream()
                .map(TemporaryPetAccess::getPet)
                .distinct()
                .filter(pet -> canAccess(user, pet))
                .toList();
    }

    private List<TemporaryPetAccess> findActive(User user, Pet pet) {
        if (user == null || pet == null || user.getId() == null || pet.getId() == null) {
            return List.of();
        }
        return accessRepository.findByUserIdAndPetId(user.getId(), pet.getId()).stream()
                .filter(access -> Boolean.TRUE.equals(access.getActive()) && access.getRevokedAt() == null)
                .toList();
    }

    private boolean isCurrentlyActive(TemporaryPetAccess access) {
        Instant now = appTimeService.nowInstant();
        if (access.getAcceptedAt() == null
                || now.isBefore(access.getStartsAt())
                || now.isAfter(access.getExpiresAt())) {
            return false;
        }
        if (!Boolean.TRUE.equals(access.getRecurring())) {
            return true;
        }
        LocalTime time = appTimeService.now().toLocalTime();
        DayOfWeek day = appTimeService.now().getDayOfWeek();
        Set<String> days = splitDays(access.getRecurringDays());
        return days.contains(day.name())
                && !time.isBefore(access.getRecurringStartTime())
                && !time.isAfter(access.getRecurringEndTime());
    }

    private void validateRequest(User owner, CreateRequest request) {
        if (request == null || request.startsAt() == null || request.expiresAt() == null) {
            throw new IllegalArgumentException("startsAt and expiresAt are required");
        }
        if (!request.startsAt().isBefore(request.expiresAt())) {
            throw new IllegalArgumentException("startsAt must be before expiresAt");
        }
        if (owner.getRole() != User.Role.ADMIN && PlanPolicy.planOf(owner) == User.Plan.BASIC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Temporary access is available for Family and Premium plans");
        }
        boolean recurring = Boolean.TRUE.equals(request.recurring());
        if (recurring && owner.getRole() != User.Role.ADMIN && PlanPolicy.planOf(owner) != User.Plan.PREMIUM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recurring schedules require Premium");
        }
        boolean anyPermission = Boolean.TRUE.equals(request.canViewLocation())
                || Boolean.TRUE.equals(request.canViewAlerts())
                || Boolean.TRUE.equals(request.canViewHistory())
                || request.canViewLocation() == null && request.canViewAlerts() == null && request.canViewHistory() == null;
        if (!anyPermission) {
            throw new IllegalArgumentException("At least one permission is required");
        }
        if (recurring) {
            if (request.recurringDays() == null || request.recurringDays().isEmpty()
                    || request.recurringStartTime() == null || request.recurringEndTime() == null
                    || !request.recurringStartTime().isBefore(request.recurringEndTime())) {
                throw new IllegalArgumentException("Recurring access requires days and a valid daily time range");
            }
            normalizeDays(request.recurringDays());
        }
    }

    private User resolveUser(CreateRequest request) {
        if (request.userId() != null) {
            return userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("userId or email is required");
        }
        return userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
    }

    private Set<String> normalizeDays(Set<String> days) {
        if (days == null) return Set.of();
        EnumSet<DayOfWeek> normalized = EnumSet.noneOf(DayOfWeek.class);
        for (String day : days) {
            try {
                normalized.add(DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid recurring day: " + day);
            }
        }
        return normalized.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> splitDays(String days) {
        if (days == null || days.isBlank()) return Set.of();
        return Set.of(days.split(","));
    }

    private Response toDto(TemporaryPetAccess access, Instant now) {
        User user = access.getUser();
        return new Response(access.getId(), access.getPet().getId(), user.getId(), user.getName(), user.getEmail(),
                access.getStartsAt(), access.getExpiresAt(), access.getRecurring(), splitDays(access.getRecurringDays()),
                access.getRecurringStartTime(), access.getRecurringEndTime(), access.getCanViewLocation(),
                access.getCanViewAlerts(), access.getCanViewHistory(), access.getActive(), access.getAcceptedAt() != null,
                isCurrentlyActive(access));
    }

    private Pet requirePet(Long petId) {
        return petRepository.findById(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));
    }

    private void requireOwnerOrAdmin(User current, Pet pet) {
        boolean owner = pet.getOwner() != null && current.getId().equals(pet.getOwner().getId());
        if (current.getRole() != User.Role.ADMIN && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the pet owner can manage temporary access");
        }
    }

    private String generateTemporaryPassword() {
        return generateToken().substring(0, 16);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
