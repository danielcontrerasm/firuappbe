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

    private final LocationService locationService;
    private final PetRepository petRepository;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("*** GPS HANDLER: Channel Active *** {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        log.info("*** GPS HANDLER: Received message from {} - Raw: '{}'", ctx.channel().remoteAddress(), msg);
        
        String line = msg.trim();
        if (line.isEmpty()) {
            log.warn("*** GPS HANDLER: Empty message received");
            return;
        }

        try {
            log.debug("*** GPS HANDLER: Parsing message: {}", line);
            // Format: IMEI,lat,lon,ISO-8601
            String[] parts = line.split(",");
            if (parts.length < 3) {
                log.warn("*** GPS HANDLER: Invalid format - expected at least 3 parts, got {}: {}", parts.length, line);
                return;
            }
            
            String imei = parts[0].trim();
            double lat = Double.parseDouble(parts[1]);
            double lon = Double.parseDouble(parts[2]);
            String ts = (parts.length > 3 && !parts[3].isBlank()) ? parts[3].trim() : Instant.now().toString();
            LocalDateTime timestamp = LocalDateTime.from(Instant.parse(ts));
            
            log.info("*** GPS HANDLER: Parsed - IMEI: {}, Lat: {}, Lon: {}, Time: {}", imei, lat, lon, timestamp);
            
            Optional<Pet> petOpt = petRepository.findByImei(imei);
            if (petOpt.isEmpty()) {
                log.warn("*** GPS HANDLER: GPS event for unknown IMEI={} (no pet bound). Skipping persist.", imei);
                return;
            }
            Pet pet = petOpt.get();
            log.info("*** GPS HANDLER: Found pet: id={}, name={}, imei={}", pet.getId(), pet.getName(), imei);

            Location loc = Location.builder()
                    .pet(pet)
                    .timestamp(timestamp)
                    .latitude(lat)
                    .longitude(lon)
                    .build();

            locationService.save(loc);
            log.info("*** GPS HANDLER: SUCCESSFULLY persisted location for pet id={} imei={} @ {},{}", 
                pet.getId(), imei, lat, lon);
            
        } catch (NumberFormatException nfe) {
            log.warn("*** GPS HANDLER: Failed to parse coordinates - Message: '{}', Error: {}", line, nfe.getMessage());
        } catch (Exception ex) {
            log.error("*** GPS HANDLER: EXCEPTION processing message '{}': {}", line, ex.getMessage(), ex);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("*** GPS HANDLER: Exception caught - Remote: {}, Error: {}", 
            ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }
}

