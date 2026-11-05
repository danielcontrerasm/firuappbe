package com.example.pettracker.controller;

import com.example.pettracker.service.PetService;
import com.example.pettracker.service.ImageUploadLimits;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP requests: MockMvc multipart requests bypass the servlet upload-size checks. */
@SpringBootTest(classes = PetImageUploadLimitIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
class PetImageUploadLimitIntegrationTest {
    private static final int MB = 1024 * 1024;
    @Autowired private TestRestTemplate http;
    @MockBean private PetService petService;

    @Test
    void acceptsImageLargerThanTheOldFiveMbLimit() {
        AtomicLong receivedImageSize = new AtomicLong(-1);
        when(petService.update(eq(1L), any(), any(), isNull())).thenAnswer(call -> {
            // Servlet upload temp files are deleted when the request completes; capture size now.
            receivedImageSize.set(call.getArgument(2, MultipartFile.class).getSize());
            return null;
        });
        ResponseEntity<String> response = upload(6 * MB, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receivedImageSize.get()).isEqualTo(6L * MB);
        verify(petService).update(eq(1L), any(), any(), isNull());
    }

    @Test
    void rejectsFileOverTwentyMbWithAnActionable413Response() {
        ResponseEntity<String> response = upload(20 * MB + 1, 0);

        assertTooLarge(response);
    }

    @Test
    void rejectsTotalRequestOverTwentyOneMbEvenWhenEachFileFits() {
        ResponseEntity<String> response = upload(12 * MB, 10 * MB);

        assertTooLarge(response);
    }

    private void assertTooLarge(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).contains("20 MB", "21 MB");
        verifyNoInteractions(petService);
    }

    private ResponseEntity<String> upload(int imageSize, int extraFileSize) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("pet", new HttpEntity<>("{\"name\":\"Sofía\"}", jsonHeaders));
        parts.add("image", file("pet.jpg", imageSize));
        if (extraFileSize > 0) parts.add("extra", file("extra.jpg", extraFileSize));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return http.exchange("/api/pets/1", HttpMethod.PUT, new HttpEntity<>(parts, headers), String.class);
    }

    private HttpEntity<ByteArrayResource> file(String name, int size) {
        ByteArrayResource bytes = new ByteArrayResource(new byte[size]) {
            @Override public String getFilename() { return name; }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        return new HttpEntity<>(bytes, headers);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({PetController.class, ApiExceptionHandler.class, ImageUploadLimits.class})
    static class TestApplication {}
}
