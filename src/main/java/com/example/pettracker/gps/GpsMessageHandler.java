// com.example.pettracker.gps.GpsMessageHandler
package com.example.pettracker.gps;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.service.LocationService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GpsMessageHandler extends SimpleChannelInboundHandler<String> {

    //private final GpsKafkaProducer producer;
    private final LocationService locationService;
    private final PetRepository petRepository;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String line = msg.trim();
        if (line.isEmpty()) return;

        try {
            // Format: IMEI,lat,lon,ISO-8601
            String[] parts = line.split(",");
            String imei = parts[0].trim();
            double lat = Double.parseDouble(parts[1]);
            double lon = Double.parseDouble(parts[2]);
            String ts = (parts.length > 3 && !parts[3].isBlank()) ? parts[3].trim() : Instant.now().toString();
            LocalDateTime timestamp = LocalDateTime.from(Instant.parse(ts));
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
                    .timestamp(timestamp)
                    .latitude(lat)
                    .longitude(lon)
                    //.position(p)
                    .build();

            locationService.save(loc);
            log.info("Persisted location for pet id={} imei={} @ {},{}", pet.getId(), imei, lat, lon);
            //producer.sendLocation(imei, lat, lon, ts);
            log.debug("Accepted GPS line -> {}", line);
        } catch (Exception ex) {
            log.warn("Failed to parse GPS message '{}': {}", line, ex.getMessage());
        }
    }
}
