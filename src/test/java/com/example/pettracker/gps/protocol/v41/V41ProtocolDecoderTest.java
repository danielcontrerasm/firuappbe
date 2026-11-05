package com.example.pettracker.gps.protocol.v41;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class V41ProtocolDecoderTest {

    @ParameterizedTest
    @MethodSource("validBatteryReports")
    void decodesBatteryPercentIncludingZeroAndFullCharge(boolean versioned, int percent) {
        V41ProtocolDecoder.GpsPosition position = decodePosition(versioned, String.format("e101%02x", percent));

        assertThat(position.getBattery()).isEqualTo(percent);
        assertThat(position.getBatteryVoltage()).isNull();
        assertThat(position.getAdditionalFields()).containsEntry("batteryPercentRaw", String.valueOf(percent));
    }

    @ParameterizedTest
    @MethodSource("invalidBatteryReports")
    void rejectsOutOfRangeAndSentinelValuesWithoutClamping(boolean versioned, int percent) {
        V41ProtocolDecoder.GpsPosition position = decodePosition(versioned, String.format("e101%02x", percent));

        assertThat(position.getBattery()).isNull();
        assertThat(position.getBatteryVoltage()).isNull();
        assertThat(position.getLatitude()).isEqualTo(4.710989);
        assertThat(position.getAdditionalFields()).containsEntry("batteryPercentRaw", String.valueOf(percent));
    }

    @ParameterizedTest
    @MethodSource("unsupportedBatteryLayouts")
    void missingMalformedAndUnknownBatteryFieldsDoNotProducePercentages(boolean versioned, String fields) {
        V41ProtocolDecoder.GpsPosition position = decodePosition(versioned, fields);

        assertThat(position.getBattery()).isNull();
        assertThat(position.getBatteryVoltage()).isNull();
        assertThat(position.getLatitude()).isEqualTo(4.710989);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multiByteFieldDoesNotBecomeZeroPercentOrCorruptFollowingFields(boolean versioned) {
        V41ProtocolDecoder.GpsPosition position = decodePosition(versioned, "e102005701040000000a");

        assertThat(position.getBattery()).isNull();
        assertThat(position.getAdditionalFields())
                .containsEntry("malformed_E1", "length=2")
                .containsEntry("0xE1", "0057");
        assertThat(position.getMileageKm()).isEqualTo(1.0);
    }

    private static Stream<Arguments> validBatteryReports() {
        return batteryReports(0, 1, 87, 100);
    }

    private static Stream<Arguments> invalidBatteryReports() {
        return batteryReports(101, 128, 255);
    }

    private static Stream<Arguments> batteryReports(int... percentages) {
        return Stream.of(false, true).flatMap(versioned -> IntStream.of(percentages)
                .mapToObj(percent -> Arguments.of(versioned, percent)));
    }

    private static Stream<Arguments> unsupportedBatteryLayouts() {
        return Stream.of(false, true).flatMap(versioned -> Stream.of(
                        "", // battery omitted
                        "e100", // empty value
                        "e1020057", // multi-byte value with a plausible first byte
                        "e1025780", // multi-byte value starting with a valid percentage
                        "e10257", // truncated value
                        "e101", // value missing after TLV header
                        "e20157") // unknown vendor field, not evidence of battery percentage
                .map(fields -> Arguments.of(versioned, fields)));
    }

    private V41ProtocolDecoder.GpsPosition decodePosition(boolean versioned, String fields) {
        V41ProtocolDecoder decoder = new V41ProtocolDecoder(ZoneId.of("America/Bogota"));
        ByteBuf frame = Unpooled.wrappedBuffer(locationFrame(versioned, HexFormat.of().parseHex(fields)));
        try {
            V41ProtocolDecoder.DecodeResult result = decoder.decode(frame);
            assertThat(result.getPosition()).isNotNull();
            return result.getPosition();
        } finally {
            frame.release();
        }
    }

    private byte[] locationFrame(boolean versioned, byte[] additionalFields) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeInt(body, 0); // alarm flags
        writeInt(body, 0x02); // status flags: GPS valid
        writeInt(body, 4_710_989); // latitude * 1_000_000
        writeInt(body, 74_072_580); // longitude * 1_000_000
        writeShort(body, 0); // altitude
        writeShort(body, 0); // speed
        writeShort(body, 0); // course
        body.writeBytes(new byte[] {0x26, 0x10, 0x04, 0x12, 0x34, 0x56});
        body.writeBytes(additionalFields);

        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeShort(payload, 0x0200);
        writeShort(payload, bodyBytes.length | (versioned ? 0x4000 : 0));
        if (versioned) payload.write(1);
        payload.writeBytes(HexFormat.of().parseHex(versioned ? "00000001234567890123" : "123456789012"));
        writeShort(payload, 1);
        payload.writeBytes(bodyBytes);

        byte[] payloadBytes = payload.toByteArray();
        int checksum = 0;
        for (byte value : payloadBytes) {
            checksum ^= value & 0xFF;
        }

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x7E);
        for (byte value : payloadBytes) {
            writeEscaped(frame, value & 0xFF);
        }
        writeEscaped(frame, checksum);
        frame.write(0x7E);
        return frame.toByteArray();
    }

    private void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private void writeInt(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >>> 24) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    private void writeEscaped(ByteArrayOutputStream out, int value) {
        if (value == 0x7E) {
            out.write(0x7D);
            out.write(0x02);
        } else if (value == 0x7D) {
            out.write(0x7D);
            out.write(0x01);
        } else {
            out.write(value);
        }
    }
}
