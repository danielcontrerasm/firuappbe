// com.example.pettracker.gps.GpsMessageHandler
package com.example.pettracker.gps;

import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.DecodeResult;
import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.GpsPosition;
import com.example.pettracker.service.GpsIngestionService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AttributeKey;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class GpsMessageHandler extends SimpleChannelInboundHandler<ByteBuf> {

    //private final GpsKafkaProducer producer;
    private static final int MAX_HEX_LOG_LENGTH = 512;
    private static final int V41_FRAME_FLAG = 0x7E;
    private static final int ASCII_FRAME_START = '[';
    private static final AttributeKey<String> DEVICE_IMEI = AttributeKey.valueOf("gps.device.imei");
    private static final Pattern IMEI_PATTERN = Pattern.compile("\\b\\d{14,17}\\b");

    private final GpsIngestionService gpsIngestionService;
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
        log.info("Received GPS TCP frame remote={} bytes={} hex={}", remoteAddress, bytes, truncateHex(frameHex));

        if (!frame.isReadable()) {
            log.warn("Ignoring empty GPS TCP frame remote={}", remoteAddress);
            return;
        }

        int firstByte = frame.getUnsignedByte(frame.readerIndex());
        if (firstByte == ASCII_FRAME_START) {
            handleAsciiBracketFrame(ctx, frame, remoteAddress, frameHex);
            return;
        }

        if (firstByte != V41_FRAME_FLAG) {
            log.warn("Unsupported GPS frame remote={} firstByte=0x{} hex={}",
                    remoteAddress, String.format("%02X", firstByte), truncateHex(frameHex));
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
                return;
            }

            persistLocation(result.getPosition(), remoteAddress);
        } catch (Exception ex) {
            log.warn("Failed to process GPS V41 frame remote={} hex={}: {}",
                    remoteAddress, truncateHex(frameHex), ex.getMessage(), ex);
        }
    }

    private void handleAsciiBracketFrame(ChannelHandlerContext ctx, ByteBuf frame, String remoteAddress, String frameHex) {
        String raw = frame.toString(StandardCharsets.US_ASCII);
        log.info("Received GPS ASCII frame remote={} raw='{}'", remoteAddress, sanitizeForLog(raw));

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
            String imeiFromPayload = extractImei(payload);

            if (imeiFromPayload != null) {
                ctx.channel().attr(DEVICE_IMEI).set(imeiFromPayload);
                log.info("GPS ASCII learned IMEI remote={} terminalId={} imei={} command={}",
                        remoteAddress, terminalId, imeiFromPayload, command);
            }

            String knownImei = ctx.channel().attr(DEVICE_IMEI).get();
            String effectiveImei = knownImei != null ? knownImei : terminalId;

            log.info("Decoded GPS ASCII frame remote={} protocol={} terminalId={} declaredLength={} command={} payload='{}' effectiveImei={}",
                    remoteAddress,
                    protocol,
                    terminalId,
                    declaredLength,
                    command,
                    sanitizeForLog(payload),
                    effectiveImei);

            Coordinates coordinates = extractCoordinates(payload);
            if (coordinates == null) {
                log.info("GPS ASCII frame has no coordinates remote={} command={} terminalId={} effectiveImei={}",
                        remoteAddress, command, terminalId, effectiveImei);
                return;
            }

            enqueueDecodedLocation(
                    "ASCII_BRACKET",
                    effectiveImei,
                    coordinates.latitude(),
                    coordinates.longitude(),
                    Instant.now(),
                    true,
                    remoteAddress,
                    "command=" + command + ",terminalId=" + terminalId
            );
        } catch (Exception ex) {
            log.warn("Failed to process GPS ASCII frame remote={} hex={} raw='{}': {}",
                    remoteAddress, truncateHex(frameHex), sanitizeForLog(raw), ex.getMessage(), ex);
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
            log.info("Parsed GPS V41 location remote={} imei={} lat={} lon={} timestamp={} gpsValid={} speed={} course={} alarm={} additionalFields={}",
                    remoteAddress,
                    imei,
                    lat,
                    lon,
                    LocalDateTime.ofInstant(instant, ZoneOffset.UTC),
                    position.isGpsValid(),
                    position.getSpeed(),
                    position.getCourse(),
                    position.getAlarm(),
                    position.getAdditionalFields());

            enqueueDecodedLocation("V41", imei, lat, lon, instant, position.isGpsValid(), remoteAddress, position.toString());
            //producer.sendLocation(imei, lat, lon, ts);
        } catch (Exception ex) {
            log.warn("Failed to persist GPS V41 location remote={} imei={} position={}: {}",
                    remoteAddress, imei, position, ex.getMessage(), ex);
        }
    }

    private void enqueueDecodedLocation(
            String protocol,
            String imei,
            double lat,
            double lon,
            Instant instant,
            boolean gpsValid,
            String remoteAddress,
            String context
    ) {
        log.info("Enqueuing GPS {} location for async persistence imei={} lat={} lon={} timestamp={} gpsValid={} remote={} context='{}'",
                protocol, imei, lat, lon, LocalDateTime.ofInstant(instant, ZoneOffset.UTC), gpsValid, remoteAddress, context);
        gpsIngestionService.processDecodedLocation(protocol, imei, lat, lon, instant, gpsValid, remoteAddress, context);
    }

    private String payloadCommand(String payload) {
        int commaIndex = payload.indexOf(',');
        return commaIndex < 0 ? payload : payload.substring(0, commaIndex);
    }

    private String extractImei(String payload) {
        Matcher matcher = IMEI_PATTERN.matcher(payload);
        return matcher.find() ? matcher.group() : null;
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

            if (lat > 90 || lon > 180) {
                continue;
            }

            if ("S".equalsIgnoreCase(latHemisphere)) {
                lat = -lat;
            }
            if ("W".equalsIgnoreCase(lonHemisphere)) {
                lon = -lon;
            }

            log.info("GPS ASCII coordinates extracted lat={} lon={} from payload='{}'", lat, lon, sanitizeForLog(payload));
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

    private String sanitizeForLog(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private record Coordinates(double latitude, double longitude) {
    }
}
