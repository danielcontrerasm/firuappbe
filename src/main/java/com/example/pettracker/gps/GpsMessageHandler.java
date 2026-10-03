// com.example.pettracker.gps.GpsMessageHandler
package com.example.pettracker.gps;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.service.LocationService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class GpsMessageHandler extends SimpleChannelInboundHandler<String> {

    //private final GpsKafkaProducer producer;
    private final LocationService locationService;
    private final PetRepository petRepository;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("GPS TCP client connected remote={}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("GPS TCP client disconnected remote={}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String remoteAddress = String.valueOf(ctx.channel().remoteAddress());
        String line = msg.trim();
        log.info("Received GPS TCP data remote={} bytes={} raw='{}'", remoteAddress, msg.length(), sanitizeForLog(line));
        if (line.isEmpty()) {
            log.warn("Ignoring empty GPS TCP message remote={}", remoteAddress);
            return;
        }

        try {
            // Format: IMEI,lat,lon,ISO-8601
            String[] parts = line.split(",");
            if (parts.length < 3) {
                log.warn("Invalid GPS message remote={} fields={} expected='IMEI,lat,lon,ISO-8601' raw='{}'",
                        remoteAddress, parts.length, sanitizeForLog(line));
                return;
            }
            String imei = parts[0].trim();
            double lat = Double.parseDouble(parts[1]);
            double lon = Double.parseDouble(parts[2]);
            String ts = (parts.length > 3 && !parts[3].isBlank()) ? parts[3].trim() : Instant.now().toString();
            LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.parse(ts), ZoneOffset.UTC);
            log.info("Parsed GPS message remote={} imei={} lat={} lon={} timestamp={}",
                    remoteAddress, imei, lat, lon, timestamp);
            Optional<Pet> petOpt = petRepository.findByImei(imei);
            if (petOpt.isEmpty()) {
                log.warn("GPS event for unknown IMEI={} remote={} (no pet bound). Skipping persist.", imei, remoteAddress);
                return;
            }
            Pet pet = petOpt.get();
            log.info("Matched GPS IMEI={} to pet id={} remote={}", imei, pet.getId(), remoteAddress);

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
            log.info("Persisted GPS location petId={} imei={} lat={} lon={} timestamp={} remote={}",
                    pet.getId(), imei, lat, lon, timestamp, remoteAddress);
            //producer.sendLocation(imei, lat, lon, ts);
        } catch (Exception ex) {
            log.warn("Failed to process GPS message remote={} raw='{}': {}", remoteAddress, sanitizeForLog(line), ex.getMessage(), ex);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("GPS TCP channel error remote={}: {}", ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }

    private String sanitizeForLog(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
