package com.example.pettracker.service;

import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.entity.Location;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class NeighborhoodLookupService {

    private final RestClient restClient;
    private final boolean enabled;

    public NeighborhoodLookupService(
            RestClient.Builder restClientBuilder,
            @Value("${geocoding.nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${geocoding.nominatim.user-agent:pettracker/1.0}") String userAgent,
            @Value("${geocoding.nominatim.enabled:true}") boolean enabled) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
        this.enabled = enabled;
    }

    public PetNeighborhoodDto resolveNeighborhood(Location location) {
        if (location == null) {
            return null;
        }
        if (!enabled) {
            return unresolved(location, "reverse-geocoding-disabled");
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", location.getLatitude())
                            .queryParam("lon", location.getLongitude())
                            .queryParam("zoom", 18)
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return unresolved(location, "reverse-geocoding-empty-response");
            }

            JsonNode address = response.path("address");
            String neighborhood = firstText(address,
                    "neighbourhood", "suburb", "quarter", "residential", "borough", "hamlet");
            String displayName = text(response, "display_name");
            String district = firstText(address, "city_district", "district");
            String city = firstText(address, "city", "municipality", "town", "county");
            if (district == null) {
                district = extractComunaFromDisplayName(displayName, city, neighborhood);
            }
            boolean resolved = isMedellin(city, district) && neighborhood != null;

            return new PetNeighborhoodDto(
                    location.getPet() == null ? null : location.getPet().getId(),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getTimestamp() == null ? null : location.getTimestamp().toString(),
                    neighborhood,
                    district,
                    city,
                    displayName,
                    "nominatim",
                    resolved
            );
        } catch (Exception exception) {
            return unresolved(location, "reverse-geocoding-failed");
        }
    }

    private PetNeighborhoodDto unresolved(Location location, String source) {
        return new PetNeighborhoodDto(
                location.getPet() == null ? null : location.getPet().getId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp() == null ? null : location.getTimestamp().toString(),
                null,
                null,
                null,
                null,
                source,
                false
        );
    }

    private boolean isMedellin(String city, String district) {
        return containsMedellin(city) || containsMedellin(district);
    }

    private boolean containsMedellin(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains("medellin");
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String extractComunaFromDisplayName(String displayName, String city, String neighborhood) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        String[] parts = displayName.split(",");
        String normalizedNeighborhood = normalize(neighborhood);
        int cityIndex = -1;

        for (int i = 0; i < parts.length; i++) {
            String part = clean(parts[i]);
            if (part == null) {
                continue;
            }
            if (containsComuna(part)) {
                return part;
            }
            if (containsMedellin(part) || (city != null && normalize(part).equals(normalize(city)))) {
                cityIndex = i;
            }
        }

        if (cityIndex > 0) {
            for (int i = cityIndex - 1; i >= 0; i--) {
                String candidate = clean(parts[i]);
                if (candidate == null) {
                    continue;
                }
                String normalizedCandidate = normalize(candidate);
                if (normalizedCandidate.equals(normalizedNeighborhood)) {
                    continue;
                }
                if (isAdministrativeTail(candidate)) {
                    continue;
                }
                return candidate;
            }
        }

        return null;
    }

    private boolean containsComuna(String value) {
        return normalize(value).contains("comuna");
    }

    private boolean isAdministrativeTail(String value) {
        String normalized = normalize(value);
        return normalized.contains("valle de aburra")
                || normalized.contains("antioquia")
                || normalized.contains("colombia")
                || normalized.contains("rap del agua y la montana");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
