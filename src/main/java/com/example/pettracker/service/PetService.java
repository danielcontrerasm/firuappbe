package com.example.pettracker.service;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.LocationRequest;
import com.example.pettracker.dto.PetDto;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.dto.PetWalkDistanceDayDto;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.PetMapper;
import com.example.pettracker.repository.PetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PetService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final PetRepository petRepository;
    private final GpsIngestionService gpsIngestionService;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final LocationService locationService;
    private final PetWalkDistanceService petWalkDistanceService;
    private final PetMapper petMapper;
    private final ImageUploadLimits imageUploadLimits;
    private final PetAccessService petAccessService;
    @Autowired
    private LostPetNotificationService lostPetNotificationService;

    @Autowired
    public PetService(
            PetRepository petRepository,
            GpsIngestionService gpsIngestionService,
            CurrentUserService currentUserService,
            UserService userService,
            LocationService locationService,
            PetWalkDistanceService petWalkDistanceService,
            PetMapper petMapper,
            ImageUploadLimits imageUploadLimits,
            PetAccessService petAccessService) {
        this.petRepository = petRepository;
        this.gpsIngestionService = gpsIngestionService;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.locationService = locationService;
        this.petWalkDistanceService = petWalkDistanceService;
        this.petMapper = petMapper;
        this.imageUploadLimits = imageUploadLimits;
        this.petAccessService = petAccessService;
    }

    /** Backward-compatible constructor used by lightweight unit tests. */
    public PetService(
            PetRepository petRepository,
            GpsIngestionService gpsIngestionService,
            CurrentUserService currentUserService,
            UserService userService,
            LocationService locationService,
            PetWalkDistanceService petWalkDistanceService,
            PetMapper petMapper,
            ImageUploadLimits imageUploadLimits) {
        this.petRepository = petRepository;
        this.gpsIngestionService = gpsIngestionService;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.locationService = locationService;
        this.petWalkDistanceService = petWalkDistanceService;
        this.petMapper = petMapper;
        this.imageUploadLimits = imageUploadLimits;
        this.petAccessService = null;
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> findLastLocationsForCurrentUser(Authentication authentication) {
        User user = currentUserService.require(authentication);
        return locationService.findLastLocationsByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<PetDto> listForCurrentUser(Authentication authentication, int page, int size) {
        User user = currentUserService.require(authentication);
        return listForUser(user, page, size).stream()
                .map(petMapper::toDtoWithNeighborhood)
                .toList();
    }

    @Transactional(readOnly = true)
    public PetDto getForCurrentUser(Long id, Authentication authentication) {
        return petMapper.toDtoWithNeighborhood(requireAccessiblePet(id, authentication));
    }

    @Transactional
    public PetDto create(PetDto request, MultipartFile image, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pet pet = new Pet();
        petMapper.applyToEntity(pet, request);
        pet.setOwner(resolveOwner(user, request.ownerId()));

        Pet saved = image == null ? create(pet) : create(pet, image);
        return petMapper.toDtoWithNeighborhood(saved);
    }

    @Transactional
    public PetDto update(Long id, PetDto request, MultipartFile image, Authentication authentication) {
        Pet existing = requireManageablePet(id, authentication);
        User user = currentUserService.require(authentication);
        petMapper.applyToEntity(existing, request);
        if (user.getRole() == User.Role.ADMIN && request.ownerId() != null) {
            existing.setOwner(resolveOwner(user, request.ownerId()));
        }

        Pet saved = image == null ? update(existing) : update(existing, image);
        return petMapper.toDtoWithNeighborhood(saved);
    }

    @Transactional(readOnly = true)
    public PetImage findAccessibleImageById(Long id, Authentication authentication) {
        User user = currentUserService.require(authentication);
        PetImage image = findImageById(id);
        if (image == null) {
            throw notFound("Pet image not found");
        }
        Pet pet = petRepository.findById(id).orElse(null);
        boolean canView = petAccessService != null
                ? petAccessService.canAccess(user, pet)
                : user.getRole() == User.Role.ADMIN || Objects.equals(user.getId(), image.ownerId());
        if (!canView) {
            throw forbidden();
        }
        return image;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        requireManageablePet(id, authentication);
        delete(id);
    }

    @Transactional
    public PetDto markPetAsLost(Long id, Authentication authentication) {
        requireManageablePet(id, authentication);
        return petMapper.toDtoWithNeighborhood(markPetAsLost(id));
    }

    @Transactional
    public PetDto markPetAsFound(Long id, Authentication authentication) {
        requireManageablePet(id, authentication);
        return petMapper.toDtoWithNeighborhood(markPetAsFound(id));
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getLocations(Long id, Authentication authentication) {
        User user = currentUserService.require(authentication);
        requireAccessiblePet(id, authentication);
        LocalDateTime now = locationService.now();
        LocalDateTime from = now.minusDays(defaultHistoryWindowDays(user));
        return locationService.getPetRouteBetween(id, from.toString(), now.toString());
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getRouteLast3Hours(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return locationService.getPetRouteLast3Hours(id);
    }

    @Transactional(readOnly = true)
    public List<LocationDTO> getRouteBetween(Long id, String from, String to, Authentication authentication) {
        User user = currentUserService.require(authentication);
        requireAccessiblePet(id, authentication);
        LocalDateTime fromTimestamp = locationService.parseRequestTimestamp(from);
        LocalDateTime toTimestamp = locationService.parseRequestTimestamp(to);
        if (from == null || from.isBlank()) {
            fromTimestamp = toTimestamp.minusDays(defaultHistoryWindowDays(user));
        }
        if (fromTimestamp.isAfter(toTimestamp)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        PlanPolicy.requireHistoryWindow(user, fromTimestamp, locationService.now());
        return locationService.getPetRouteBetween(id, fromTimestamp.toString(), toTimestamp.toString());
    }

    @Transactional(readOnly = true)
    public byte[] exportHistory(Long id, String from, String to, Authentication authentication) {
        ExportHistoryData report = loadExportHistory(id, from, to, authentication);
        StringBuilder csv = new StringBuilder("id,petId,petName,timestamp,latitude,longitude,batteryPercent,batteryVoltage,city,address,neighborhood,comuna\n");
        report.locations().forEach(location -> csv.append(csv(location.id())).append(',')
                .append(csv(location.petId())).append(',')
                .append(csv(location.petName())).append(',')
                .append(csv(location.timestamp())).append(',')
                .append(location.latitude()).append(',')
                .append(location.longitude()).append(',')
                .append(csv(location.batteryPercent())).append(',')
                .append(csv(location.batteryVoltage())).append(',')
                .append(csv(location.city())).append(',')
                .append(csv(location.address())).append(',')
                .append(csv(location.neighborhood())).append(',')
                .append(csv(location.comuna())).append('\n'));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportHistoryPdf(Long id, String from, String to, Authentication authentication) {
        ExportHistoryData report = loadExportHistory(id, from, to, authentication);
        return buildPdf(report);
    }

    private ExportHistoryData loadExportHistory(
            Long id,
            String from,
            String to,
            Authentication authentication) {
        User user = currentUserService.require(authentication);
        PlanPolicy.requirePremiumExport(user);
        Pet pet = requireAccessiblePet(id, authentication);

        LocalDateTime now = locationService.now();
        LocalDateTime end = to == null || to.isBlank() ? now : locationService.parseRequestTimestamp(to);
        LocalDateTime start = from == null || from.isBlank()
                ? end.minusDays(defaultHistoryWindowDays(user))
                : locationService.parseRequestTimestamp(from);
        PlanPolicy.requireHistoryWindow(user, start, now);
        List<LocationDTO> locations = locationService.getPetRouteBetweenForExport(id, start, end);
        return new ExportHistoryData(pet, start, end, locations);
    }

    private byte[] buildPdf(ExportHistoryData report) {
        List<String> lines = new ArrayList<>();
        lines.add("PetTracker - Reporte de rutas");
        lines.add("Mascota: " + safeText(report.pet().getName()) + " (#" + report.pet().getId() + ")");
        lines.add("Periodo: " + report.from() + " a " + report.to());
        lines.add("Puntos registrados: " + report.locations().size());
        lines.add("");
        lines.add("Fecha y hora                         Latitud        Longitud       Bateria");
        for (LocationDTO location : report.locations()) {
            String battery = location.batteryPercent() == null ? "-" : location.batteryPercent() + "%";
            lines.add(String.format(Locale.ROOT,
                    "%s   %.6f   %.6f   %s",
                    safeText(location.timestamp()),
                    location.latitude(),
                    location.longitude(),
                    battery));
        }

        final int linesPerPage = 54;
        int pageCount = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
        int firstPageId = 3;
        int firstContentId = firstPageId + pageCount;
        int fontId = firstContentId + pageCount;
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");

        StringBuilder pageKids = new StringBuilder();
        for (int page = 0; page < pageCount; page++) {
            pageKids.append(firstPageId + page).append(" 0 R ");
        }
        objects.add("<< /Type /Pages /Kids [" + pageKids + "] /Count " + pageCount + " >>");

        for (int page = 0; page < pageCount; page++) {
            int contentId = firstContentId + page;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] "
                    + "/Resources << /Font << /F1 " + fontId + " 0 R >> >> "
                    + "/Contents " + contentId + " 0 R >>");
        }

        for (int page = 0; page < pageCount; page++) {
            int start = page * linesPerPage;
            int end = Math.min(lines.size(), start + linesPerPage);
            StringBuilder content = new StringBuilder("BT\n/F1 9 Tf\n40 800 Td\n");
            for (int index = start; index < end; index++) {
                content.append('(').append(pdfText(lines.get(index))).append(") Tj\n0 -14 Td\n");
            }
            content.append("ET\n");
            byte[] contentBytes = content.toString().getBytes(StandardCharsets.US_ASCII);
            objects.add("<< /Length " + contentBytes.length + " >>\nstream\n"
                    + content + "endstream");
        }
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        try {
            return writePdf(objects);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate PDF report", exception);
        }
    }

    private byte[] writePdf(List<String> objects) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAscii(output, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            writeAscii(output, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        int xrefOffset = output.size();
        writeAscii(output, "xref\n0 " + (objects.size() + 1) + "\n");
        writeAscii(output, "0000000000 65535 f \n");
        for (Integer offset : offsets) {
            writeAscii(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        writeAscii(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"
                + "startxref\n" + xrefOffset + "\n%%EOF\n");
        return output.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String pdfText(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : safeText(value).toCharArray()) {
            if (character < 32 || character > 126) {
                escaped.append('?');
            } else if (character == '(' || character == ')' || character == '\\') {
                escaped.append('\\').append(character);
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private String safeText(String value) {
        return value == null ? "-" : value;
    }

    private record ExportHistoryData(Pet pet, LocalDateTime from, LocalDateTime to, List<LocationDTO> locations) {
    }

    private String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private int defaultHistoryWindowDays(User user) {
        return user.getRole() == User.Role.ADMIN ? 365 : PlanPolicy.historyDays(user);
    }

    @Transactional
    public PetNeighborhoodDto getNeighborhood(Long id, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        Location latestLocation = locationService.getLatestByPetId(id);
        if (latestLocation == null) {
            throw notFound("Pet location not found");
        }
        return locationService.getNeighborhoodByPetId(id);
    }

    @Transactional(readOnly = true)
    public List<PetWalkDistanceDayDto> getWeeklyWalkDistance(Long id, LocalDate weekStart, Authentication authentication) {
        requireAccessiblePet(id, authentication);
        return petWalkDistanceService.getWeeklyDistance(id, weekStart);
    }

    @Transactional
    public LocationDTO addLocation(Long id, LocationRequest request, Authentication authentication) {
        Pet pet = requireManageablePet(id, authentication);
        LocalDateTime timestamp = locationService.parseRequestTimestamp(request.getTimestamp());
        Location location = Location.builder()
                .pet(pet)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timestamp(timestamp)
                .batteryPercent(request.getBatteryPercent())
                .batteryVoltage(request.getBatteryVoltage())
                .build();

        return locationService.save(location);
    }

    public Pet create(Pet p) {
        Pet saved = petRepository.save(p);
        gpsIngestionService.evictPetCache(saved.getTerminalId());
        return saved;
    }

    public Pet create(Pet pet, MultipartFile image) {
        setImageIfPresent(pet, image);
        Pet saved = petRepository.save(pet);
        gpsIngestionService.evictPetCache(saved.getTerminalId());
        return saved;
    }

    public List<Pet> listForUser(User user) {
        if (user.getRole() == User.Role.ADMIN) return petRepository.findAll();
        return petRepository.findAccessibleForUser(user.getId());
    }

    public List<Pet> listForUser(User user, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        if (user.getRole() == User.Role.ADMIN) {
            return petRepository.findAll(pageable).getContent();
        }
        return petRepository.findAccessibleForUser(user.getId(), pageable);
    }

    private Pageable pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));
    }

    private Pet requireAccessiblePet(Long id, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pet pet = petRepository.findById(id).orElseThrow(() -> notFound("Pet not found"));
        if (!canAccessPet(user, pet)) {
            throw forbidden();
        }
        return pet;
    }

    private Pet requireManageablePet(Long id, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Pet pet = petRepository.findById(id).orElseThrow(() -> notFound("Pet not found"));
        boolean ownerOrAdmin = user.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null && user.getId().equals(pet.getOwner().getId()));
        if (!ownerOrAdmin) {
            throw forbidden();
        }
        return pet;
    }

    private boolean canAccessPet(User user, Pet pet) {
        if (petAccessService != null && petAccessService.canAccess(user, pet)) {
            return true;
        }
        return user.getRole() == User.Role.ADMIN
                || (pet.getOwner() != null
                && pet.getOwner().getId() != null
                && pet.getOwner().getId().equals(user.getId()));
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        if (currentUser.getRole() == User.Role.ADMIN && ownerId != null) {
            User owner = userService.findById(ownerId);
            if (owner == null) {
                throw notFound("Owner not found");
            }
            return owner;
        }
        return currentUser;
    }

    private ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this pet");
    }

    public Pet findById(Long id) {
        return petRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public PetImage findImageById(Long id) {
        Pet pet = petRepository.findById(id).orElse(null);
        if (pet == null || pet.getImageData() == null || pet.getImageContentType() == null) {
            return null;
        }
        return new PetImage(
                pet.getOwner() == null ? null : pet.getOwner().getId(),
                pet.getImageData(),
                pet.getImageContentType(),
                pet.getImageFileName()
        );
    }

    public Pet update(Pet p) {
        String previousTerminalId = findPreviousTerminalId(p);
        Pet saved = petRepository.save(p);
        evictTerminalIdCache(previousTerminalId, saved.getTerminalId());
        return saved;
    }

    public Pet update(Pet pet, MultipartFile image) {
        String previousTerminalId = findPreviousTerminalId(pet);
        setImageIfPresent(pet, image);
        Pet saved = petRepository.save(pet);
        evictTerminalIdCache(previousTerminalId, saved.getTerminalId());
        return saved;
    }

    private String findPreviousTerminalId(Pet pet) {
        if (pet == null || pet.getId() == null) {
            return null;
        }
        return petRepository.findById(pet.getId())
                .map(Pet::getTerminalId)
                .orElse(null);
    }

    private void evictTerminalIdCache(String previousTerminalId, String currentTerminalId) {
        gpsIngestionService.evictPetCache(previousTerminalId);
        if (currentTerminalId == null || currentTerminalId.equals(previousTerminalId)) {
            return;
        }
        gpsIngestionService.evictPetCache(currentTerminalId);
    }

    private void setImageIfPresent(Pet pet, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        validateImage(image);
        try {
            pet.setImageContentType(image.getContentType());
            pet.setImageFileName(image.getOriginalFilename());
            pet.setImageData(image.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Could not read pet image", e);
        }
    }

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Pet image must be JPEG, PNG, WebP, or GIF");
        }
        imageUploadLimits.validateImageSize(image.getSize());
    }

    public record PetImage(Long ownerId, byte[] data, String contentType, String fileName) {
        public String fileNameOrDefault() {
            return fileName == null || fileName.isBlank() ? "pet-image" : fileName;
        }
    }

    public void delete(Long id) {
        Pet pet = petRepository.findById(id).orElse(null);
        petRepository.deleteById(id);
        if (pet != null) {
            gpsIngestionService.evictPetCache(pet.getTerminalId());
        }
    }
    // ... existing code ...


    public Pet markPetAsLost(Long petId) {
        log.info("Entered markPetAsLost in PetService with petId: {}", petId);
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setStatus(Pet.Status.LOST);
        Pet updatedPet = petRepository.save(pet);

        // Send notifications to owner
        lostPetNotificationService.notifyPetLost(updatedPet.getId());

        return updatedPet;
    }

    public Pet markPetAsFound(Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setStatus(Pet.Status.ACTIVE);
        Pet updatedPet = petRepository.save(pet);

        // Send notifications to owner
        lostPetNotificationService.notifyPetFound(updatedPet.getId());

        return updatedPet;
    }

// ... existing code ...


}
