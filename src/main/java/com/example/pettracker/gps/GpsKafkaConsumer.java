// com.example.pettracker.kafka.GpsKafkaConsumer
package com.example.pettracker.gps;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.Location;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GpsKafkaConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    private final PetRepository petRepository;
    private final LocationRepository locationRepository;

   /* @KafkaListener(topics = "${gps.kafka.topic:gps-locations}", groupId = "${spring.kafka.consumer.group-id:pettracker-gps-consumer}")
    public void onMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String imei = node.get("imei").asText();
            double lat = node.get("lat").asDouble();
            double lon = node.get("lon").asDouble();
            Instant ts = Instant.parse(node.get("timestamp").asText());

            Optional<Pet> petOpt = petRepository.findByImei(imei);
            if (petOpt.isEmpty()) {
                log.warn("GPS event for unknown IMEI={} (no pet bound). Skipping persist.", imei);
                return;
            }
            Pet pet = petOpt.get();

            //Point p = gf.createPoint(new Coordinate(lon, lat));
            //p.setSRID(4326);

            Location loc = Location.builder()
                    .pet(pet)
                    .timestamp(ts)
                    .latitude(lat)
                    .longitude(lon)
                    //.position(p)
                    .build();

            locationRepository.save(loc);
            log.info("Persisted location for pet id={} imei={} @ {},{}", pet.getId(), imei, lat, lon);

            // TODO: trigger geofence check + alerts here if you want
        } catch (Exception ex) {
            log.error("Failed to process GPS Kafka message: {}", ex.getMessage(), ex);
        }
    }*/
}
