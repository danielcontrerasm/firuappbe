package com.example.pettracker.topin;

import java.time.Instant;

/**
 * Immutable messages from the supported Topin/ZhongXun layouts.
 * Times are UTC, speed is km/h, coordinates are decimal degrees and altitude is metres.
 * No database writes or socket operations occur in these value objects.
 */
public sealed interface TopinMessage {
    int type();

    int declaredLength();

    record Login(int declaredLength, String imei, int softwareVersion) implements TopinMessage {
        @Override
        public int type() {
            return 0x01;
        }
    }

    /**
     * {@code historical} identifies an offline report; it does not change a 0x10/0x11 fix flag.
     * For the distinct 0x08/0x09 GPS layout, {@code valid} follows Traccar's type convention:
     * true for 0x08 and false for 0x09. That layout has no independently decoded fix flag.
     * {@code altitudeMeters} is null when the frame does not contain an altitude field.
     * {@code alarmBits} preserves all bits instead of reducing simultaneous alarms to one label.
     */
    record Gps(
            int declaredLength,
            int type,
            Instant deviceTime,
            double latitude,
            double longitude,
            int speedKph,
            int courseDegrees,
            boolean valid,
            int satellites,
            Integer altitudeMeters,
            int alarmBits,
            boolean historical) implements TopinMessage {
    }

    /**
     * The timezone byte is deliberately kept raw: the supported documents do not establish
     * one signed-offset encoding for every firmware. extraHex retains all extension bytes,
     * including the signal byte when present, without assigning undocumented health metrics.
     */
    record Status(
            int declaredLength,
            int batteryPercent,
            int softwareVersion,
            int timeZoneByte,
            int intervalMinutes,
            Integer signal,
            String extraHex) implements TopinMessage {
        @Override
        public int type() {
            return 0x13;
        }
    }

    record Heartbeat(int declaredLength) implements TopinMessage {
        @Override
        public int type() {
            return 0x08;
        }
    }

    record TimeRequest(int declaredLength) implements TopinMessage {
        @Override
        public int type() {
            return 0x30;
        }
    }

    record Alarm(int declaredLength, int type, String name) implements TopinMessage {
    }

    record Sleep(int declaredLength) implements TopinMessage {
        @Override
        public int type() {
            return 0x14;
        }
    }
}
