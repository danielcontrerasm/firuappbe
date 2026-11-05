package com.example.pettracker.service;

import com.example.pettracker.dto.PetDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.mapper.PetMapper;
import com.example.pettracker.repository.PetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PetImageServiceTest {
    private final PetRepository pets = mock(PetRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final PetMapper mapper = new PetMapper(mock(LocationService.class));
    private PetService service;
    private Pet pet;

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(9L).role(User.Role.USER).build();
        pet = Pet.builder().id(1L).name("Sofía").type("DOG").terminalId("014166272782")
                .owner(owner).imageContentType(MediaType.IMAGE_JPEG_VALUE).imageFileName("old.jpg")
                .imageData(new byte[] {1, 2, 3}).build();
        when(currentUser.require(authentication)).thenReturn(owner);
        when(pets.findById(1L)).thenReturn(Optional.of(pet));
        when(pets.save(any(Pet.class))).thenAnswer(call -> call.getArgument(0));
        service = serviceWithLimit(DataSize.ofMegabytes(20));
    }

    @Test
    void replacesImageBytesAndMetadataAndReturnsTheNewImage() {
        byte[] replacement = {(byte) 0x89, 0x50, 0x4e, 0x47};
        MockMultipartFile upload = new MockMultipartFile("image", "new.png", MediaType.IMAGE_PNG_VALUE, replacement);

        PetDto response = service.update(1L, mapper.toDto(pet), upload, authentication);
        PetService.PetImage image = service.findAccessibleImageById(1L, authentication);

        verify(pets).save(pet);
        assertThat(response.imageUrl()).isEqualTo("/api/pets/1/image");
        assertThat(image.data()).containsExactly(replacement);
        assertThat(image.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(image.fileName()).isEqualTo("new.png");
        assertThat(pet.getTerminalId()).isEqualTo("014166272782");
    }

    @Test
    void updatingDetailsWithoutAnUploadKeepsTheExistingImage() {
        service.update(1L, mapper.toDto(pet), null, authentication);

        assertThat(pet.getImageData()).containsExactly(1, 2, 3);
        assertThat(pet.getImageFileName()).isEqualTo("old.jpg");
        assertThat(pet.getImageContentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
    }

    @Test
    void anotherUserCannotReplaceThePetImage() {
        when(currentUser.require(authentication)).thenReturn(User.builder().id(10L).role(User.Role.USER).build());
        MockMultipartFile upload = new MockMultipartFile("image", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[] {4});

        assertThatThrownBy(() -> service.update(1L, mapper.toDto(pet), upload, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(pets, never()).save(any());
        assertThat(pet.getImageData()).containsExactly(1, 2, 3);
    }

    @Test
    void acceptsImageAboveTheOldFiveMbLimit() {
        MockMultipartFile upload = new MockMultipartFile("image", "large.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[6 * 1024 * 1024]);

        service.update(1L, mapper.toDto(pet), upload, authentication);

        verify(pets).save(pet);
        assertThat(pet.getImageData()).hasSize(6 * 1024 * 1024);
    }

    @Test
    void rejectsOversizedImageBeforeReadingBytesOrChangingTheExistingImage() throws Exception {
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getContentType()).thenReturn(MediaType.IMAGE_JPEG_VALUE);
        when(upload.getSize()).thenReturn(DataSize.ofMegabytes(20).toBytes() + 1);

        assertThatThrownBy(() -> service.update(1L, mapper.toDto(pet), upload, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(error.getReason()).contains("20 MB");
                });

        verify(upload, never()).getBytes();
        verify(pets, never()).save(any());
        assertThat(pet.getImageData()).containsExactly(1, 2, 3);
    }

    @Test
    void acceptsImageExactlyAtTheConfiguredLimit() {
        service = serviceWithLimit(DataSize.ofKilobytes(1));
        MockMultipartFile upload = new MockMultipartFile("image", "small.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[1024]);

        service.update(1L, mapper.toDto(pet), upload, authentication);

        assertThat(pet.getImageData()).hasSize(1024);
    }

    @Test
    void honorsACustomLimitInsteadOfKeepingAHardcodedCap() {
        service = serviceWithLimit(DataSize.ofKilobytes(1));
        MockMultipartFile upload = new MockMultipartFile("image", "small.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[1025]);

        assertThatThrownBy(() -> service.update(1L, mapper.toDto(pet), upload, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(error.getReason()).contains("1 KB");
                });
        verify(pets, never()).save(any());
    }

    private PetService serviceWithLimit(DataSize limit) {
        MultipartProperties properties = new MultipartProperties();
        properties.setMaxFileSize(limit);
        properties.setMaxRequestSize(DataSize.ofMegabytes(21));
        return new PetService(pets, mock(GpsIngestionService.class), currentUser,
                mock(UserService.class), mock(LocationService.class), mock(PetWalkDistanceService.class), mapper,
                new ImageUploadLimits(properties));
    }
}
