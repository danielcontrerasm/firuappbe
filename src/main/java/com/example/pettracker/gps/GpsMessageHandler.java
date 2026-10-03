// com.example.pettracker.gps.GpsMessageHandler
package com.example.pettracker.gps;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.DecodeResult;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.GpsPosition;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.service.LocationService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
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
public class GpsMessageHandler extends SimpleChannelInboundHandler<ByteBuf> {

    //private final GpsKafkaProducer producer;
    private static final int MAX_HEX_LOG_LENGTH = 512;

    private final LocationService locationService;
    private final PetRepository petRepository;
    private final V41ProtocolDecoder v41ProtocolDecoder = new V41ProtocolDecoder();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("GPS TCP client connected remote={}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("GPS TCP client disconnected remote={}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        String remoteAddress = String.valueOf(ctx.channel().remoteAddress());
        int bytes = frame.readableBytes();
        String frameHex = ByteBufUtil.hexDump(frame, frame.readerIndex(), bytes);
        log.info("Received GPS V41 frame remote={} bytes={} hex={}", remoteAddress, bytes, truncateHex(frameHex));

        if (!frame.isReadable()) {
            log.warn("Ignoring empty GPS V41 frame remote={}", remoteAddress);
            return;
        }

        try {
            DecodeResult result = v41ProtocolDecoder.decode(frame);
            log.info("Decoded GPS V41 message remote={} messageType={} messageId=0x{} terminalId={} sequence={}",
                    remoteAddress,
                    result.getMessageType(),
                    String.format("%04X", result.getMessageId()),
                    result.getTerminalId(),
                    result.getSequence());

            if (result.getPosition() == null) {
                log.info("GPS V41 message has no location payload remote={} messageType={} terminalId={} sequence={}",
                        remoteAddress, result.getMessageType(), result.getTerminalId(), result.getSequence());
                logPetBindingStatus(result.getTerminalId(), remoteAddress);
                return;
            }

            persistLocation(result.getPosition(), remoteAddress);
        } catch (Exception ex) {
            log.warn("Failed to process GPS V41 frame remote={} hex={}: {}",
                    remoteAddress, truncateHex(frameHex), ex.getMessage(), ex);
        }
    }

    private void persistLocation(GpsPosition position, String remoteAddress) {
        String imei = position.getTerminalId();
        if (imei == null || imei.isBlank()) {
            log.warn("GPS V41 location missing terminalId remote={} position={}", remoteAddress, position);
            return;
        }

        if (position.getLatitude() == null || position.getLongitude() == null) {
            log.warn("GPS V41 location missing coordinates remote={} imei={} position={}",
                    remoteAddress, imei, position);
            return;
        }

        try {
            double lat = position.getLatitude();
            double lon = position.getLongitude();
            Instant instant = position.getTimestamp() == null ? Instant.now() : position.getTimestamp();
            LocalDateTime timestamp = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            log.info("Parsed GPS V41 location remote={} imei={} lat={} lon={} timestamp={} gpsValid={} speed={} course={} alarm={} additionalFields={}",
                    remoteAddress,
                    imei,
                    lat,
                    lon,
                    timestamp,
                    position.isGpsValid(),
                    position.getSpeed(),
                    position.getCourse(),
                    position.getAlarm(),
                    position.getAdditionalFields());

            Optional<Pet> petOpt = petRepository.findByImei(imei);
            if (petOpt.isEmpty()) {
                log.warn("GPS V41 location for unknown IMEI={} remote={} (no pet bound). Skipping persist.", imei, remoteAddress);
                return;
            }
            Pet pet = petOpt.get();
            log.info("Matched GPS V41 IMEI={} to pet id={} remote={}", imei, pet.getId(), remoteAddress);

            Location loc = Location.builder()
                    .pet(pet)
                    .timestamp(timestamp)
                    .latitude(lat)
                    .longitude(lon)
                    //.position(p)
                    .build();

            locationService.save(loc);
            log.info("Persisted GPS V41 location petId={} imei={} lat={} lon={} timestamp={} gpsValid={} remote={}",
                    pet.getId(), imei, lat, lon, timestamp, position.isGpsValid(), remoteAddress);
            //producer.sendLocation(imei, lat, lon, ts);
        } catch (Exception ex) {
            log.warn("Failed to persist GPS V41 location remote={} imei={} position={}: {}",
                    remoteAddress, imei, position, ex.getMessage(), ex);
        }
    }

    private void logPetBindingStatus(String imei, String remoteAddress) {
        if (imei == null || imei.isBlank()) {
            log.warn("GPS V41 message missing terminalId remote={}", remoteAddress);
            return;
        }

        boolean knownPet = petRepository.findByImei(imei).isPresent();
        log.info("GPS V41 terminal binding remote={} imei={} knownPet={}", remoteAddress, imei, knownPet);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("GPS TCP channel error remote={}: {}", ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }

    private String truncateHex(String hex) {
        if (hex == null || hex.length() <= MAX_HEX_LOG_LENGTH) {
            return hex;
        }
        return hex.substring(0, MAX_HEX_LOG_LENGTH) + "...(truncated," + hex.length() + " hex chars)";
    }
}
