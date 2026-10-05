// com.example.pettracker.gps.GpsMessageHandler
package com.example.pettracker.gps;

import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.DecodeResult;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.GpsPosition;
import com.example.pettracker.service.AppTimeService;
import com.example.pettracker.service.GpsIngestionService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class GpsMessageHandler extends SimpleChannelInboundHandler<ByteBuf> {

    //private final GpsKafkaProducer producer;
    private static final int MAX_HEX_LOG_LENGTH = 512;
    private static final int V41_FRAME_FLAG = 0x7E;
    private static final int ASCII_FRAME_START = '[';

    private final GpsIngestionService gpsIngestionService;
    private final AppTimeService appTimeService;

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
        // Expensive success-path log: full-frame hex is noisy under live GPS traffic.
        // log.info("Received GPS TCP frame remote={} bytes={} hex={}", remoteAddress, bytes, truncateHex(hexDumpFrame(frame)));

        if (!frame.isReadable()) {
            log.warn("Ignoring empty GPS TCP frame remote={}", remoteAddress);
            return;
        }

        int firstByte = frame.getUnsignedByte(frame.readerIndex());
        if (firstByte == ASCII_FRAME_START) {
            handleAsciiBracketFrame(ctx, frame, remoteAddress);
            return;
        }

        if (firstByte != V41_FRAME_FLAG) {
            log.warn("Unsupported GPS frame remote={} firstByte=0x{} hex={}",
                    remoteAddress, String.format("%02X", firstByte), truncateHex(hexDumpFrame(frame)));
            return;
        }

        try {
            DecodeResult result = new V41ProtocolDecoder(appTimeService.zoneId()).decode(frame);
            // Expensive success-path log: every V41 frame is already validated and failures still warn below.
            // log.info("Decoded GPS V41 message remote={} messageType={} messageId=0x{} terminalId={} sequence={}",
            //         remoteAddress,
            //         result.getMessageType(),
            //         String.format("%04X", result.getMessageId()),
            //         result.getTerminalId(),
            //         result.getSequence());

            if (result.getPosition() == null) {
                // Expensive success-path log: heartbeats and auth frames can be high-volume.
                // log.info("GPS V41 message has no location payload remote={} messageType={} terminalId={} sequence={}",
                //         remoteAddress, result.getMessageType(), result.getTerminalId(), result.getSequence());
                return;
            }

            persistLocation(result.getPosition(), remoteAddress);
        } catch (Exception ex) {
            log.warn("Failed to process GPS V41 frame remote={} hex={}: {}",
                    remoteAddress, truncateHex(hexDumpFrame(frame)), ex.getMessage(), ex);
        }
    }

    private void handleAsciiBracketFrame(ChannelHandlerContext ctx, ByteBuf frame, String remoteAddress) {
        String raw = frame.toString(StandardCharsets.US_ASCII);
        // Expensive success-path log: raw device payloads are noisy under live GPS traffic.
        // log.info("Received GPS ASCII frame remote={} raw='{}'", remoteAddress, sanitizeForLog(raw));

        try {
            if (!raw.startsWith("[") || !raw.endsWith("]")) {
                log.warn("Invalid GPS ASCII frame boundaries remote={} raw='{}'", remoteAddress, sanitizeForLog(raw));
                return;
            }

            String content = raw.substring(1, raw.length() - 1);
            String[] headerParts = content.split("\\*", 4);
            if (headerParts.length < 4) {
                log.warn("Invalid GPS ASCII frame structure remote={} fields={} raw='{}'",
                        remoteAddress, headerParts.length, sanitizeForLog(raw));
                return;
            }

            String protocol = headerParts[0];
            String terminalId = headerParts[1];
            String declaredLength = headerParts[2];
            String payload = headerParts[3];
            String command = payloadCommand(payload);

            // Expensive success-path log: payload sanitization and full payload logging are noisy.
            // log.info("Decoded GPS ASCII frame remote={} protocol={} terminalId={} declaredLength={} command={} payload='{}'",
            //         remoteAddress,
            //         protocol,
            //         terminalId,
            //         declaredLength,
            //         command,
            //         sanitizeForLog(payload));

            sendAsciiAckIfRequired(ctx, protocol, terminalId, command, remoteAddress);

            Coordinates coordinates = extractCoordinates(payload);
            if (coordinates == null) {
                // Expensive success-path log: non-location ASCII frames can be frequent.
                // log.info("GPS ASCII frame has no coordinates remote={} command={} terminalId={}",
                //         remoteAddress, command, terminalId);
                return;
            }

            enqueueDecodedLocation(
                    "ASCII_BRACKET",
                    terminalId,
                    coordinates.latitude(),
                    coordinates.longitude(),
                    Instant.now(),
                    true,
                    remoteAddress,
                    "command=" + command + ",terminalId=" + terminalId
            );
        } catch (Exception ex) {
            log.warn("Failed to process GPS ASCII frame remote={} hex={} raw='{}': {}",
                    remoteAddress, truncateHex(hexDumpFrame(frame)), sanitizeForLog(raw), ex.getMessage(), ex);
        }
    }

    private void persistLocation(GpsPosition position, String remoteAddress) {
        String terminalId = position.getTerminalId();
        if (terminalId == null || terminalId.isBlank()) {
            log.warn("GPS V41 location missing terminalId remote={} position={}", remoteAddress, position);
            return;
        }

        if (position.getLatitude() == null || position.getLongitude() == null) {
            log.warn("GPS V41 location missing coordinates remote={} terminalId={} position={}",
                    remoteAddress, terminalId, position);
            return;
        }

        try {
            double lat = position.getLatitude();
            double lon = position.getLongitude();
            Instant instant = position.getTimestamp() == null ? Instant.now() : position.getTimestamp();
            // Expensive success-path log: adds per-location timestamp conversion and additional field rendering.
            // log.info("Parsed GPS V41 location remote={} terminalId={} lat={} lon={} timestamp={} gpsValid={} speed={} course={} alarm={} additionalFields={}",
            //         remoteAddress,
            //         terminalId,
            //         lat,
            //         lon,
            //         appTimeService.fromInstant(instant),
            //         position.isGpsValid(),
            //         position.getSpeed(),
            //         position.getCourse(),
            //         position.getAlarm(),
            //         position.getAdditionalFields());

            enqueueDecodedLocation(
                    "V41",
                    terminalId,
                    lat,
                    lon,
                    instant,
                    position.isGpsValid(),
                    position.getBattery(),
                    position.getBatteryVoltage(),
                    remoteAddress,
                    "terminalId=" + terminalId + ",protocol=V41"
            );
            //producer.sendLocation(terminalId, lat, lon, ts);
        } catch (Exception ex) {
            log.warn("Failed to persist GPS V41 location remote={} terminalId={} position={}: {}",
                    remoteAddress, terminalId, position, ex.getMessage(), ex);
        }
    }

    private void enqueueDecodedLocation(
            String protocol,
            String terminalId,
            double lat,
            double lon,
            Instant instant,
            boolean gpsValid,
            String remoteAddress,
            String context
    ) {
        enqueueDecodedLocation(protocol, terminalId, lat, lon, instant, gpsValid, null, null, remoteAddress, context);
    }

    private void enqueueDecodedLocation(
            String protocol,
            String terminalId,
            double lat,
            double lon,
            Instant instant,
            boolean gpsValid,
            Integer batteryPercent,
            Double batteryVoltage,
            String remoteAddress,
            String context
    ) {
        // Expensive success-path log: every decoded point is persisted asynchronously below.
        // log.info("Enqueuing GPS {} location for async persistence terminalId={} lat={} lon={} timestamp={} gpsValid={} batteryPercent={} batteryVoltage={} remote={} context='{}'",
        //         protocol, terminalId, lat, lon, appTimeService.fromInstant(instant), gpsValid, batteryPercent, batteryVoltage, remoteAddress, context);
        gpsIngestionService.processDecodedLocation(protocol, terminalId, lat, lon, instant, gpsValid, batteryPercent, batteryVoltage, remoteAddress, context);
    }

    private String payloadCommand(String payload) {
        int commaIndex = payload.indexOf(',');
        return commaIndex < 0 ? payload : payload.substring(0, commaIndex);
    }

    private void sendAsciiAckIfRequired(
            ChannelHandlerContext ctx,
            String protocol,
            String terminalId,
            String command,
            String remoteAddress
    ) {
        if (!requiresAsciiAck(command)) {
            log.debug("GPS ASCII command does not require ACK remote={} command={} terminalId={}",
                    remoteAddress, command, terminalId);
            return;
        }

        String response = buildAsciiFrame(protocol, terminalId, command);
        ctx.writeAndFlush(Unpooled.copiedBuffer(response, StandardCharsets.US_ASCII))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("Sent GPS ASCII ACK remote={} command={} response='{}'",
                                remoteAddress, command, response);
                    } else {
                        log.warn("Failed to send GPS ASCII ACK remote={} command={} response='{}': {}",
                                remoteAddress, command, response, future.cause().getMessage(), future.cause());
                    }
                });
    }

    private boolean requiresAsciiAck(String command) {
        return "LK".equalsIgnoreCase(command)
                || "TKQ".equalsIgnoreCase(command)
                || "TKQ2".equalsIgnoreCase(command);
    }

    private String buildAsciiFrame(String protocol, String terminalId, String command) {
        return "[" + protocol + "*" + terminalId + "*" + String.format("%04X", command.length()) + "*" + command + "]";
    }

    private Coordinates extractCoordinates(String payload) {
        String[] tokens = payload.split(",");
        for (int i = 0; i <= tokens.length - 4; i++) {
            Double lat = parseDouble(tokens[i]);
            String latHemisphere = tokens[i + 1].trim();
            Double lon = parseDouble(tokens[i + 2]);
            String lonHemisphere = tokens[i + 3].trim();

            if (lat == null || lon == null || !isHemisphere(latHemisphere, "NS") || !isHemisphere(lonHemisphere, "EW")) {
                continue;
            }

            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
                continue;
            }

            lat = "S".equalsIgnoreCase(latHemisphere) ? -Math.abs(lat) : Math.abs(lat);
            lon = "W".equalsIgnoreCase(lonHemisphere) ? -Math.abs(lon) : Math.abs(lon);

            // Expensive success-path log: coordinate extraction runs for every ASCII location payload.
            // log.info("GPS ASCII coordinates extracted lat={} lon={} from payload='{}'", lat, lon, sanitizeForLog(payload));
            return new Coordinates(lat, lon);
        }

        return null;
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isHemisphere(String value, String allowedValues) {
        return value.length() == 1 && allowedValues.toLowerCase().contains(value.toLowerCase());
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

    private String hexDumpFrame(ByteBuf frame) {
        return ByteBufUtil.hexDump(frame, frame.readerIndex(), frame.readableBytes());
    }

    private String sanitizeForLog(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private record Coordinates(double latitude, double longitude) {
    }
}
