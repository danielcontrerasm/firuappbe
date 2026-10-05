package com.example.pettracker.gps.protocol.v41;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decoder for V41 devices based on the JT/T 808 protocol.
 *
 * Flow:
 *
 * TCP -> Frame -> unescape -> checksum -> JT808 header ->
 * message body -> GpsPosition
 *
 * Relevant messages:
 *
 * 0x0002 Heartbeat
 * 0x0100 Register
 * 0x0102 Authentication
 * 0x0200 Location Report
 */
@Slf4j
public class V41ProtocolDecoder {

    private static final int FRAME_FLAG = 0x7E;
    private static final int MAX_HEX_LOG_LENGTH = 512;
    private final ZoneId timestampZone;

    public V41ProtocolDecoder() {
        this(ZoneId.of("America/Bogota"));
    }

    public V41ProtocolDecoder(ZoneId timestampZone) {
        this.timestampZone = timestampZone;
    }

    private static final int MSG_HEARTBEAT = 0x0002;
    private static final int MSG_REGISTER = 0x0100;
    private static final int MSG_AUTHENTICATION = 0x0102;
    private static final int MSG_LOCATION = 0x0200;

    /**
     * Decodes a complete TCP frame.
     *
     * @param incoming received frame
     * @return protocol decode result
     */
    public DecodeResult decode(ByteBuf incoming) {

        if (incoming == null || !incoming.isReadable()) {
            log.warn("V41 decode rejected: empty frame");
            throw new V41ProtocolException("Empty V41 frame");
        }

        int incomingBytes = incoming.readableBytes();
        String rawHex = ByteBufUtil.hexDump(
                incoming,
                incoming.readerIndex(),
                incomingBytes
        );
        // Expensive success-path log: raw frame hex can be large and is emitted for every GPS packet.
        // log.info("V41 decode started bytes={} rawHex={}", incomingBytes, truncateHex(rawHex));

        ByteBuf frame = incoming.copy();

        try {

            validateFrameBoundaries(frame);
            log.debug("V41 frame boundaries valid bytes={}", incomingBytes);

            frame.readUnsignedByte();

            int payloadLength = frame.readableBytes() - 1;
            log.debug("V41 escaped payload length={}", payloadLength);

            if (payloadLength <= 0) {
                log.warn("V41 decode rejected: invalid payload length={} rawHex={}", payloadLength, truncateHex(rawHex));
                throw new V41ProtocolException("Invalid V41 frame length");
            }

            byte[] escapedPayload = new byte[payloadLength];

            frame.readBytes(escapedPayload);
            // Expensive debug log: hex-dumps every escaped payload even when debug is disabled.
            // log.debug("V41 escaped payload hex={}", truncateHex(ByteBufUtil.hexDump(escapedPayload)));

            byte[] decodedPayload = unescape(escapedPayload);
            // Expensive debug log: hex-dumps every decoded payload even when debug is disabled.
            // log.debug("V41 unescaped payload length={} hex={}",
            //         decodedPayload.length, truncateHex(ByteBufUtil.hexDump(decodedPayload)));

            if (decodedPayload.length < 6) {
                log.warn("V41 decode rejected: payload too short length={} rawHex={}",
                        decodedPayload.length, truncateHex(rawHex));
                throw new V41ProtocolException("V41 frame too short");
            }

            validateChecksum(decodedPayload);
            // Expensive debug log: formats checksum on every valid packet.
            // log.debug("V41 checksum valid calculated/received={}",
            //         String.format("%02X", decodedPayload[decodedPayload.length - 1] & 0xFF));

            ByteBuf data = Unpooled.wrappedBuffer(
                    decodedPayload,
                    0,
                    decodedPayload.length - 1
            );

            Jt808Header header = decodeHeader(data);
            // Expensive success-path log: repeated for every decoded packet.
            // log.info("V41 header decoded messageId=0x{} messageType={} terminalId={} sequence={} bodyLength={} subPackage={} remainingBytes={}",
            //         String.format("%04X", header.messageId),
            //         messageType(header.messageId),
            //         header.terminalId,
            //         header.sequence,
            //         header.bodyLength,
            //         header.subPackage,
            //         data.readableBytes());

            DecodeResult result = new DecodeResult();

            result.messageId = header.messageId;
            result.terminalId = header.terminalId;
            result.sequence = header.sequence;
            result.rawHex = rawHex;

            switch (header.messageId) {

                case MSG_LOCATION -> {
                    result.messageType = "LOCATION";
                    result.position = decodeLocation(data, header, rawHex);
                    // Expensive success-path log: renders decoded positions and additional fields per location.
                    // log.info("V41 location decoded terminalId={} lat={} lon={} speed={} course={} altitude={} gpsValid={} accOn={} timestamp={} alarm={} additionalFields={}",
                    //         result.position.getTerminalId(),
                    //         result.position.getLatitude(),
                    //         result.position.getLongitude(),
                    //         result.position.getSpeed(),
                    //         result.position.getCourse(),
                    //         result.position.getAltitude(),
                    //         result.position.isGpsValid(),
                    //         result.position.isAccOn(),
                    //         result.position.getTimestamp(),
                    //         result.position.getAlarm(),
                    //         result.position.getAdditionalFields());
                }

                case MSG_HEARTBEAT -> {
                    result.messageType = "HEARTBEAT";
                    // Expensive success-path log: heartbeats can be high-volume.
                    // log.info("V41 heartbeat received terminalId={} sequence={}", header.terminalId, header.sequence);
                }

                case MSG_REGISTER -> {
                    result.messageType = "REGISTER";
                    // Expensive success-path log: normal registration flow does not need per-packet info logs.
                    // log.info("V41 register received terminalId={} sequence={} bodyBytes={}",
                    //         header.terminalId, header.sequence, data.readableBytes());
                }

                case MSG_AUTHENTICATION -> {
                    result.messageType = "AUTHENTICATION";
                    // Expensive success-path log: normal authentication flow does not need per-packet info logs.
                    // log.info("V41 authentication received terminalId={} sequence={} bodyBytes={}",
                    //         header.terminalId, header.sequence, data.readableBytes());
                }

                default -> {
                    result.messageType = String.format(
                            "UNKNOWN_0x%04X",
                            header.messageId
                    );
                    log.warn("V41 unknown message received messageId=0x{} terminalId={} sequence={} bodyBytes={} rawHex={}",
                            String.format("%04X", header.messageId),
                            header.terminalId,
                            header.sequence,
                            data.readableBytes(),
                            truncateHex(rawHex));
                }
            }

            // Expensive success-path log: DecodeResult.toString() includes nested position details.
            // log.info("V41 decode completed result={}", result);
            return result;

        } finally {
            frame.release();
        }
    }

    private void validateFrameBoundaries(ByteBuf frame) {

        if (frame.readableBytes() < 2) {
            log.warn("V41 frame boundary validation failed: too short bytes={}", frame.readableBytes());
            throw new V41ProtocolException("Frame too short");
        }

        int first = frame.getUnsignedByte(frame.readerIndex());
        int last = frame.getUnsignedByte(frame.writerIndex() - 1);

        if (first != FRAME_FLAG) {
            log.warn("V41 frame boundary validation failed: firstByte=0x{} expected=0x7E",
                    String.format("%02X", first));
            throw new V41ProtocolException("V41 frame does not start with 0x7E");
        }

        if (last != FRAME_FLAG) {
            log.warn("V41 frame boundary validation failed: lastByte=0x{} expected=0x7E",
                    String.format("%02X", last));
            throw new V41ProtocolException("V41 frame does not end with 0x7E");
        }
    }

    /**
     * JT/T 808 escaping:
     *
     * 7D 02 -> 7E
     * 7D 01 -> 7D
     */
    private byte[] unescape(byte[] data) {

        ByteBuf output = Unpooled.buffer(data.length);

        try {

            for (int i = 0; i < data.length; i++) {

                int current = data[i] & 0xFF;

                if (current == 0x7D && i + 1 < data.length) {

                    int next = data[i + 1] & 0xFF;

                    if (next == 0x02) {
                        log.trace("V41 unescape sequence 7D02 -> 7E at index={}", i);
                        output.writeByte(0x7E);
                        i++;
                        continue;
                    } else if (next == 0x01) {
                        log.trace("V41 unescape sequence 7D01 -> 7D at index={}", i);
                        output.writeByte(0x7D);
                        i++;
                        continue;
                    }
                }

                output.writeByte(current);
            }

            byte[] result = new byte[output.readableBytes()];
            output.readBytes(result);

            return result;

        } finally {
            output.release();
        }
    }

    /**
     * JT/T 808 normally uses XOR from Message ID through the last body byte.
     */
    private void validateChecksum(byte[] data) {

        if (data.length < 2) {
            log.warn("V41 checksum validation failed: payload too short length={}", data.length);
            throw new V41ProtocolException("Cannot validate checksum");
        }

        int calculated = 0;

        for (int i = 0; i < data.length - 1; i++) {
            calculated ^= data[i] & 0xFF;
        }

        int received = data[data.length - 1] & 0xFF;

        if (calculated != received) {
            log.warn("V41 checksum invalid calculated=0x{} received=0x{} payloadHex={}",
                    String.format("%02X", calculated),
                    String.format("%02X", received),
                    truncateHex(ByteBufUtil.hexDump(data)));
            throw new V41ProtocolException(
                    String.format(
                            "Invalid checksum. Expected=%02X Received=%02X",
                            calculated,
                            received
                    )
            );
        }

        // Expensive trace log: formats checksum on every valid packet.
        // log.trace("V41 checksum valid value=0x{}", String.format("%02X", received));
    }

    private Jt808Header decodeHeader(ByteBuf buffer) {

        ensureReadable(buffer, 12);

        int messageId = buffer.readUnsignedShort();
        int messageProperties = buffer.readUnsignedShort();

        int bodyLength = messageProperties & 0x03FF;
        boolean subPackage = (messageProperties & 0x2000) != 0;

        byte[] terminalBytes = new byte[6];
        buffer.readBytes(terminalBytes);

        String terminalId = decodeBcd(terminalBytes);

        int sequence = buffer.readUnsignedShort();
        // Expensive debug log: formats header fields on every packet.
        // log.debug("V41 raw header messageId=0x{} messageProperties=0x{} terminalId={} sequence={} bodyLength={}",
        //         String.format("%04X", messageId),
        //         String.format("%04X", messageProperties),
        //         terminalId,
        //         sequence,
        //         bodyLength);

        Integer packageTotal = null;
        Integer packageNumber = null;

        if (subPackage) {
            ensureReadable(buffer, 4);
            packageTotal = buffer.readUnsignedShort();
            packageNumber = buffer.readUnsignedShort();
            log.debug("V41 subpackage header packageTotal={} packageNumber={}", packageTotal, packageNumber);
        }

        Jt808Header header = new Jt808Header();

        header.messageId = messageId;
        header.messageProperties = messageProperties;
        header.bodyLength = bodyLength;
        header.terminalId = terminalId;
        header.sequence = sequence;
        header.subPackage = subPackage;
        header.packageTotal = packageTotal;
        header.packageNumber = packageNumber;

        if (buffer.readableBytes() < bodyLength) {
            log.warn("V41 body length mismatch messageId=0x{} terminalId={} expectedBodyLength={} available={}",
                    String.format("%04X", messageId),
                    terminalId,
                    bodyLength,
                    buffer.readableBytes());
            throw new V41ProtocolException(
                    "JT808 body says "
                            + bodyLength
                            + " bytes but only "
                            + buffer.readableBytes()
                            + " are available"
            );
        }

        return header;
    }

    private GpsPosition decodeLocation(ByteBuf buffer, Jt808Header header, String rawHex) {

        ensureReadable(buffer, 28);
        log.debug("V41 decoding location body terminalId={} readableBytes={}", header.terminalId, buffer.readableBytes());

        GpsPosition position = new GpsPosition();

        position.protocol = "V41";
        position.terminalId = header.terminalId;
        position.rawMessage = rawHex;

        long alarm = buffer.readUnsignedInt();
        position.alarmFlags = alarm;
        // Expensive debug log: formats flags for every location packet.
        // log.debug("V41 location alarmFlags=0x{}", String.format("%08X", alarm));

        long status = buffer.readUnsignedInt();
        position.statusFlags = status;
        // Expensive debug log: formats flags for every location packet.
        // log.debug("V41 location statusFlags=0x{}", String.format("%08X", status));

        long latitudeRaw = buffer.readUnsignedInt();
        long longitudeRaw = buffer.readUnsignedInt();
        log.debug("V41 location rawCoordinates latitudeRaw={} longitudeRaw={}", latitudeRaw, longitudeRaw);

        double latitude = latitudeRaw / 1_000_000.0;
        double longitude = longitudeRaw / 1_000_000.0;

        boolean south = (status & (1L << 2)) != 0;
        boolean west = (status & (1L << 3)) != 0;

        latitude = south ? -Math.abs(latitude) : Math.abs(latitude);
        longitude = west ? -Math.abs(longitude) : Math.abs(longitude);
        log.debug("V41 location decodedCoordinates latitude={} longitude={} south={} west={}",
                latitude, longitude, south, west);

        position.latitude = latitude;
        position.longitude = longitude;
        position.altitude = buffer.readUnsignedShort();

        int speedRaw = buffer.readUnsignedShort();
        position.speed = speedRaw / 10.0;

        position.course = buffer.readUnsignedShort();
        log.debug("V41 location movement altitude={} speedRaw={} speed={} course={}",
                position.altitude, speedRaw, position.speed, position.course);

        byte[] datetime = new byte[6];
        buffer.readBytes(datetime);

        position.timestamp = decodeDateTime(datetime);
        // Expensive debug log: hex-dumps BCD timestamp for every location packet.
        // log.debug("V41 location datetime rawBcd={} timestamp={}",
        //         ByteBufUtil.hexDump(datetime), position.timestamp);
        position.gpsValid = (status & 0x02) != 0;
        position.accOn = (status & 0x01) != 0;
        position.alarm = decodeAlarm(alarm);
        log.debug("V41 location flags gpsValid={} accOn={} alarm={}",
                position.gpsValid, position.accOn, position.alarm);

        decodeAdditionalFields(buffer, position);

        return position;
    }

    private void decodeAdditionalFields(ByteBuf buffer, GpsPosition position) {

        while (buffer.readableBytes() >= 2) {

            int id = buffer.readUnsignedByte();
            int length = buffer.readUnsignedByte();
            // Expensive debug log: formats additional-field ids for every field.
            // log.debug("V41 additional field found id=0x{} length={} remainingBeforeValue={}",
            //         String.format("%02X", id), length, buffer.readableBytes());

            if (length > buffer.readableBytes()) {
                log.warn("V41 malformed additional field id=0x{} length={} available={}",
                        String.format("%02X", id), length, buffer.readableBytes());
                position.additionalFields.put(
                        "malformed_" + String.format("%02X", id),
                        "length=" + length
                );
                return;
            }

            ByteBuf value = buffer.readSlice(length);

            switch (id) {

                case 0x01 ->
                        decodeMileage(value, position);

                case 0x02 ->
                        decodeFuel(value, position);

                case 0x03 ->
                        decodeSpeedExtension(value, position);

                case 0x04 ->
                        decodeAlarmEvent(value, position);

                case 0xE1 ->
                        decodeBatteryPercent(value, position);

                case 0xE7 ->
                        decodeV41Status(value, position);

                case 0xEE ->
                        decodeV41Lbs(value, position);

                default -> {
                    byte[] unknown = new byte[value.readableBytes()];
                    value.getBytes(value.readerIndex(), unknown);

                    position.additionalFields.put(
                            String.format("0x%02X", id),
                            ByteBufUtil.hexDump(unknown)
                    );
                    // Expensive debug log: hex-dumps unknown additional fields.
                    // log.debug("V41 unknown additional field id=0x{} valueHex={}",
                    //         String.format("%02X", id), ByteBufUtil.hexDump(unknown));
                }
            }
        }
    }

    private void decodeMileage(ByteBuf value, GpsPosition position) {

        if (value.readableBytes() < 4) {
            return;
        }

        long raw = value.readUnsignedInt();
        position.mileageKm = raw / 10.0;
        log.debug("V41 mileage decoded raw={} mileageKm={}", raw, position.mileageKm);
    }

    private void decodeFuel(ByteBuf value, GpsPosition position) {

        if (value.readableBytes() < 2) {
            return;
        }

        int raw = value.readUnsignedShort();
        position.fuelLiters = raw / 10.0;
        log.debug("V41 fuel decoded raw={} fuelLiters={}", raw, position.fuelLiters);
    }

    private void decodeSpeedExtension(ByteBuf value, GpsPosition position) {

        if (value.readableBytes() < 2) {
            return;
        }

        int raw = value.readUnsignedShort();
        position.speed = raw / 10.0;
        log.debug("V41 speed extension decoded raw={} speed={}", raw, position.speed);
    }

    private void decodeAlarmEvent(ByteBuf value, GpsPosition position) {

        if (!value.isReadable()) {
            return;
        }

        position.additionalFields.put(
                "alarmEvent",
                ByteBufUtil.hexDump(
                        value,
                        value.readerIndex(),
                        value.readableBytes()
                )
        );
        // Expensive debug log: alarm event hex is already stored in additionalFields.
        // log.debug("V41 alarm event decoded hex={}",
        //         position.additionalFields.get("alarmEvent"));
    }

    private void decodeBatteryPercent(ByteBuf value, GpsPosition position) {

        if (!value.isReadable()) {
            return;
        }

        int raw = value.readUnsignedByte();
        setBatteryPercent(position, raw);
    }

    private void setBatteryPercent(GpsPosition position, int rawPercent) {
        position.additionalFields.put("batteryPercentRaw", String.valueOf(rawPercent));
        if (rawPercent <= 100) {
            position.battery = rawPercent;
        }
    }

    private void decodeV41Status(ByteBuf value, GpsPosition position) {

        byte[] raw = new byte[value.readableBytes()];
        value.getBytes(value.readerIndex(), raw);

        position.additionalFields.put("v41StatusRaw", ByteBufUtil.hexDump(raw));
        // Expensive debug log: hex-dumps status extensions.
        // log.debug("V41 status extension raw={}", ByteBufUtil.hexDump(raw));
    }

    private void decodeV41Lbs(ByteBuf value, GpsPosition position) {

        byte[] raw = new byte[value.readableBytes()];
        value.getBytes(value.readerIndex(), raw);

        position.lbsRaw = ByteBufUtil.hexDump(raw);
        position.additionalFields.put("lbs", position.lbsRaw);
        // Expensive debug log: LBS raw hex is already stored in the position.
        // log.debug("V41 LBS extension raw={}", position.lbsRaw);
    }

    private String decodeAlarm(long alarm) {

        if (alarm == 0) {
            return "NONE";
        }

        if ((alarm & 0x00000001L) != 0) {
            return "EMERGENCY";
        }

        if ((alarm & 0x00000002L) != 0) {
            return "OVERSPEED";
        }

        if ((alarm & 0x00000004L) != 0) {
            return "FATIGUE_DRIVING";
        }

        if ((alarm & 0x00000010L) != 0) {
            return "GNSS_FAULT";
        }

        if ((alarm & 0x00000020L) != 0) {
            return "GNSS_ANTENNA_DISCONNECTED";
        }

        if ((alarm & 0x00000040L) != 0) {
            return "GNSS_ANTENNA_SHORT";
        }

        if ((alarm & 0x00000080L) != 0) {
            return "LOW_VOLTAGE";
        }

        if ((alarm & 0x00000100L) != 0) {
            return "POWER_FAILURE";
        }

        return String.format("ALARM_0x%08X", alarm);
    }

    private String decodeBcd(byte[] bytes) {

        StringBuilder builder = new StringBuilder();

        for (byte value : bytes) {
            int b = value & 0xFF;
            builder.append((b >> 4) & 0x0F);
            builder.append(b & 0x0F);
        }

        return builder.toString();
    }

    private Instant decodeDateTime(byte[] data) {

        if (data.length != 6) {
            return null;
        }

        int year = 2000 + bcd(data[0]);
        int month = bcd(data[1]);
        int day = bcd(data[2]);
        int hour = bcd(data[3]);
        int minute = bcd(data[4]);
        int second = bcd(data[5]);

        try {
            return LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(timestampZone)
                    .toInstant();
        } catch (DateTimeException ex) {
            log.warn("V41 invalid BCD datetime raw={} decoded={}-{}-{} {}:{}:{}",
                    ByteBufUtil.hexDump(data),
                    year,
                    month,
                    day,
                    hour,
                    minute,
                    second);
            return null;
        }
    }

    private int bcd(byte value) {

        int raw = value & 0xFF;

        return ((raw >> 4) & 0x0F) * 10
                + (raw & 0x0F);
    }

    private void ensureReadable(ByteBuf buffer, int bytes) {

        if (buffer.readableBytes() < bytes) {
            log.warn("V41 buffer underflow expectedBytes={} availableBytes={}", bytes, buffer.readableBytes());
            throw new V41ProtocolException(
                    "Expected at least "
                            + bytes
                            + " bytes but only "
                            + buffer.readableBytes()
                            + " available"
            );
        }
    }

    private String messageType(int messageId) {
        return switch (messageId) {
            case MSG_HEARTBEAT -> "HEARTBEAT";
            case MSG_REGISTER -> "REGISTER";
            case MSG_AUTHENTICATION -> "AUTHENTICATION";
            case MSG_LOCATION -> "LOCATION";
            default -> String.format("UNKNOWN_0x%04X", messageId);
        };
    }

    private String truncateHex(String hex) {
        if (hex == null || hex.length() <= MAX_HEX_LOG_LENGTH) {
            return hex;
        }
        return hex.substring(0, MAX_HEX_LOG_LENGTH) + "...(truncated," + hex.length() + " hex chars)";
    }

    public static class DecodeResult {

        private int messageId;
        private String messageType;
        private String terminalId;
        private int sequence;
        private String rawHex;
        private GpsPosition position;

        public int getMessageId() {
            return messageId;
        }

        public String getMessageType() {
            return messageType;
        }

        public String getTerminalId() {
            return terminalId;
        }

        public int getSequence() {
            return sequence;
        }

        public String getRawHex() {
            return rawHex;
        }

        public GpsPosition getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return "DecodeResult{"
                    + "messageId=0x" + String.format("%04X", messageId)
                    + ", messageType='" + messageType + '\''
                    + ", terminalId='" + terminalId + '\''
                    + ", sequence=" + sequence
                    + ", position=" + position
                    + '}';
        }
    }

    private static class Jt808Header {

        private int messageId;
        private int messageProperties;
        private int bodyLength;
        private String terminalId;
        private int sequence;
        private boolean subPackage;
        private Integer packageTotal;
        private Integer packageNumber;
    }

    public static class GpsPosition {

        private String terminalId;
        private String protocol;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Integer course;
        private Integer altitude;
        private Instant timestamp;
        private boolean gpsValid;
        private boolean accOn;
        private long alarmFlags;
        private long statusFlags;
        private String alarm;
        private Double mileageKm;
        private Double fuelLiters;
        private Integer battery;
        private Double batteryVoltage;
        private String lbsRaw;
        private String rawMessage;
        private final Map<String, String> additionalFields = new LinkedHashMap<>();

        public String getTerminalId() {
            return terminalId;
        }

        public String getProtocol() {
            return protocol;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public Double getSpeed() {
            return speed;
        }

        public Integer getCourse() {
            return course;
        }

        public Integer getAltitude() {
            return altitude;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public boolean isGpsValid() {
            return gpsValid;
        }

        public boolean isAccOn() {
            return accOn;
        }

        public long getAlarmFlags() {
            return alarmFlags;
        }

        public long getStatusFlags() {
            return statusFlags;
        }

        public String getAlarm() {
            return alarm;
        }

        public Double getMileageKm() {
            return mileageKm;
        }

        public Double getFuelLiters() {
            return fuelLiters;
        }

        public Integer getBattery() {
            return battery;
        }

        public Double getBatteryVoltage() {
            return batteryVoltage;
        }

        public String getLbsRaw() {
            return lbsRaw;
        }

        public String getRawMessage() {
            return rawMessage;
        }

        public Map<String, String> getAdditionalFields() {
            return additionalFields;
        }

        @Override
        public String toString() {
            return "GpsPosition{"
                    + "terminalId='" + terminalId + '\''
                    + ", protocol='" + protocol + '\''
                    + ", latitude=" + latitude
                    + ", longitude=" + longitude
                    + ", speed=" + speed
                    + ", course=" + course
                    + ", altitude=" + altitude
                    + ", timestamp=" + timestamp
                    + ", gpsValid=" + gpsValid
                    + ", battery=" + battery
                    + ", batteryVoltage=" + batteryVoltage
                    + ", alarm='" + alarm + '\''
                    + '}';
        }
    }

    public static class V41ProtocolException extends RuntimeException {

        public V41ProtocolException(String message) {
            super(message);
        }

        public V41ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
