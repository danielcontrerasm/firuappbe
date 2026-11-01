package com.example.pettracker.topin;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Pure Java 17 parser for complete Topin packets extracted by the stream decoder.
 *
 * <p>The supported layouts are grounded in the ZhongXun Topin Locator Communication
 * Protocol (May 2017) and the published Traccar Topin decoder/test vectors. This is an
 * independent implementation, not a complete implementation of every Topin firmware.
 * In particular, Wi-Fi/LBS and undocumented payload extensions are not interpreted here.
 * Packet framing and protocol replies belong to other components.</p>
 *
 * <p>Malformed dates, BCD identifiers, coordinate ranges or supported-message lengths
 * produce an IllegalArgumentException. Values are never silently clamped or rolled over.</p>
 */
public final class TopinParser {
    private TopinParser() {
    }

    public static TopinMessage parse(TopinPacket packet) {
        Objects.requireNonNull(packet, "packet");
        byte[] payload = packet.payload();
        return switch (packet.type()) {
            case 0x01 -> login(packet, payload);
            case 0x10, 0x11 -> gps(packet, payload);
            case 0x08 -> payload.length == 0
                    ? new TopinMessage.Heartbeat(packet.declaredLength())
                    : gpsV2(packet, payload);
            case 0x09 -> gpsV2(packet, payload);
            case 0x13 -> status(packet, payload);
            case 0x30 -> {
                exactLength(packet, payload, 0);
                yield new TopinMessage.TimeRequest(packet.declaredLength());
            }
            case 0x57 -> {
                exactLength(packet, payload, 0);
                yield new TopinMessage.SettingsRequest(packet.declaredLength());
            }
            case 0xb3 -> {
                String ascii = printableAscii(payload);
                String iccid = ascii != null && ascii.matches("[0-9]{19,20}") ? ascii : null;
                yield new TopinMessage.SimInfo(packet.declaredLength(), iccid, ascii,
                        HexFormat.of().formatHex(payload));
            }
            case 0xfc -> {
                // The length convention is validated by the FC-specific framer profile.
                yield new TopinMessage.DeviceInfo(packet.declaredLength(),
                        printableAscii(payload),
                        HexFormat.of().formatHex(payload));
            }
            case 0x94, 0x99 -> {
                exactLength(packet, payload, 0);
                yield new TopinMessage.Alarm(packet.declaredLength(), packet.type(),
                        packet.type() == 0x94 ? "vibration" : "sos");
            }
            case 0x14 -> {
                exactLength(packet, payload, 0);
                yield new TopinMessage.Sleep(packet.declaredLength());
            }
            default -> throw invalid(packet, "unsupported message type");
        };
    }

    private static TopinMessage.Login login(TopinPacket packet, byte[] p) {
        exactLength(packet, p, 9);
        if ((unsigned(p[0]) >>> 4) != 0) {
            throw invalid(packet, "IMEI must use a leading zero BCD padding nibble");
        }
        StringBuilder digits = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            int high = unsigned(p[i]) >>> 4;
            int low = unsigned(p[i]) & 0x0f;
            if (high > 9 || low > 9) {
                throw invalid(packet, "IMEI contains a non-decimal BCD digit");
            }
            digits.append((char) ('0' + high)).append((char) ('0' + low));
        }
        // Remove precisely the padding nibble. An IMEI is an identifier, not an integer.
        return new TopinMessage.Login(packet.declaredLength(), digits.substring(1), unsigned(p[8]));
    }

    private static TopinMessage.Gps gps(TopinPacket packet, byte[] p) {
        if (p.length != 18 && p.length != 21) {
            throw invalid(packet, "GPS payload must contain 18 or 21 bytes; received " + p.length);
        }
        Instant timestamp = time(packet, p);
        int satellites = unsigned(p[6]) & 0x0f;
        double latitude = unsignedInt(p, 7) / 1_800_000.0;
        double longitude = unsignedInt(p, 11) / 1_800_000.0;
        int flags = unsignedShort(p, 16);
        if ((flags & 0x0400) == 0) {
            latitude = -latitude;
        }
        if ((flags & 0x0800) != 0) {
            longitude = -longitude;
        }
        coordinates(packet, latitude, longitude);
        int heading = flags & 0x03ff;
        heading(packet, heading);
        Integer altitude = p.length == 21 ? (int) (short) unsignedShort(p, 18) : null;
        int alarmBits = p.length == 21 ? unsigned(p[20]) : 0;
        return new TopinMessage.Gps(packet.declaredLength(), packet.type(), timestamp,
                latitude, longitude, unsigned(p[15]), heading, (flags & 0x1000) != 0,
                satellites, altitude, alarmBits, packet.type() == 0x11);
    }

    private static TopinMessage.Gps gpsV2(TopinPacket packet, byte[] p) {
        exactLength(packet, p, 31);
        Instant timestamp = time(packet, p);
        double latitude = packedCoordinate(packet, p, 6);
        double longitude = packedCoordinate(packet, p, 10);
        coordinates(packet, latitude, longitude);
        // Bytes 14..21 contain a second coordinate pair. Its meaning is firmware-specific.
        // Bytes 25..30 are preserved in the original TopinPacket, not invented as metrics.
        int heading = unsigned(p[23]) * 2;
        heading(packet, heading);
        return new TopinMessage.Gps(packet.declaredLength(), packet.type(), timestamp,
                latitude, longitude, unsigned(p[22]), heading,
                packet.type() == 0x08, unsigned(p[24]), null, 0, packet.type() == 0x09);
    }

    private static TopinMessage.Status status(TopinPacket packet, byte[] p) {
        if (p.length < 4) {
            throw invalid(packet, "status payload must contain at least 4 bytes; received " + p.length);
        }
        int battery = unsigned(p[0]);
        if (battery > 100) {
            throw invalid(packet, "battery percentage outside 0..100: " + battery);
        }
        Integer signal = p.length > 4 ? unsigned(p[4]) : null;
        return new TopinMessage.Status(packet.declaredLength(), battery, unsigned(p[1]),
                unsigned(p[2]), unsigned(p[3]), signal, HexFormat.of().formatHex(p, 4, p.length));
    }

    private static Instant time(TopinPacket packet, byte[] p) {
        try {
            return LocalDateTime.of(2000 + unsigned(p[0]), unsigned(p[1]), unsigned(p[2]),
                    unsigned(p[3]), unsigned(p[4]), unsigned(p[5])).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(prefix(packet) + "invalid UTC device timestamp: "
                    + HexFormat.of().formatHex(p, 0, 6), e);
        }
    }

    private static double packedCoordinate(TopinPacket packet, byte[] p, int index) {
        int fraction = ((unsigned(p[index + 1]) & 0x0f) << 16)
                | (unsigned(p[index + 2]) << 8) | unsigned(p[index + 3]);
        if (fraction >= 1_000_000) {
            throw invalid(packet, "GPS v2 coordinate fraction must be below one million");
        }
        double coordinate = unsigned(p[index]) + fraction / 1_000_000.0;
        return (unsigned(p[index + 1]) & 0xf0) == 0 ? coordinate : -coordinate;
    }

    private static void coordinates(TopinPacket packet, double latitude, double longitude) {
        if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw invalid(packet, "coordinates outside latitude/longitude range: "
                    + latitude + ", " + longitude);
        }
    }

    private static void heading(TopinPacket packet, int heading) {
        if (heading > 360) {
            throw invalid(packet, "course outside 0..360 degrees: " + heading);
        }
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static String printableAscii(byte[] payload) {
        for (byte value : payload) {
            int valueUnsigned = unsigned(value);
            if (valueUnsigned < 0x20 || valueUnsigned > 0x7e) return null;
        }
        return new String(payload, StandardCharsets.US_ASCII);
    }

    private static int unsignedShort(byte[] p, int index) {
        return (unsigned(p[index]) << 8) | unsigned(p[index + 1]);
    }

    private static long unsignedInt(byte[] p, int index) {
        return ((long) unsigned(p[index]) << 24) | ((long) unsigned(p[index + 1]) << 16)
                | ((long) unsigned(p[index + 2]) << 8) | unsigned(p[index + 3]);
    }

    private static void exactLength(TopinPacket packet, byte[] payload, int expected) {
        if (payload.length != expected) {
            throw invalid(packet, "payload must contain " + expected + " bytes; received " + payload.length);
        }
    }

    private static IllegalArgumentException invalid(TopinPacket packet, String message) {
        return new IllegalArgumentException(prefix(packet) + message);
    }

    private static String prefix(TopinPacket packet) {
        return "Topin type 0x" + HexFormat.of().toHexDigits((byte) packet.type()) + ": ";
    }
}
