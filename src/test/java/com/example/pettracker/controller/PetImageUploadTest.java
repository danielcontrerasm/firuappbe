package com.example.pettracker.controller;

import com.example.pettracker.dto.PetDto;
import com.example.pettracker.service.PetService;
import com.example.pettracker.service.ImageUploadLimits;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PetImageUploadTest {
    private static final String PET_JSON = "{\"name\":\"Sofía\",\"type\":\"DOG\",\"terminalId\":\"014166272782\"}";
    private PetService petService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        petService = mock(PetService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PetController(petService))
                .setControllerAdvice(new ApiExceptionHandler(new ImageUploadLimits(new MultipartProperties())))
                .build();
    }

    @Test
    void continuesToAcceptPetJsonAsAnApplicationJsonFilePart() throws Exception {
        mvc.perform(multipart("/api/pets/1")
                        .file(new MockMultipartFile("pet", "pet.json", MediaType.APPLICATION_JSON_VALUE,
                                PET_JSON.getBytes(StandardCharsets.UTF_8)))
                        .file(image())
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk());

        verify(petService).update(eq(1L), argThat(pet -> pet.name().equals("Sofía")), any(), isNull());
    }

    @Test
    void continuesToCreatePetWithAnApplicationJsonPartAndImage() throws Exception {
        mvc.perform(multipart("/api/pets")
                        .file(new MockMultipartFile("pet", "pet.json", MediaType.APPLICATION_JSON_VALUE,
                                PET_JSON.getBytes(StandardCharsets.UTF_8)))
                        .file(image()))
                .andExpect(status().isCreated());

        verify(petService).create(argThat(pet -> pet.name().equals("Sofía")), any(), isNull());
    }

    @Test
    void continuesToAcceptOrdinaryJsonUpdatesWithoutAnImage() throws Exception {
        mvc.perform(put("/api/pets/1").contentType(MediaType.APPLICATION_JSON)
                        .content(PET_JSON.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        verify(petService).update(eq(1L), argThat(pet -> pet.name().equals("Sofía")), isNull(), isNull());
    }

    @Test
    void rejectsMalformedPetJsonWithoutUpdatingTheImage() throws Exception {
        mvc.perform(multipart("/api/pets/1")
                        .file(new MockMultipartFile("pet", "pet.json", MediaType.APPLICATION_JSON_VALUE,
                                "invalid json".getBytes(StandardCharsets.UTF_8)))
                        .file(image())
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(petService);
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "sofia.png", MediaType.IMAGE_PNG_VALUE,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
    }
}
