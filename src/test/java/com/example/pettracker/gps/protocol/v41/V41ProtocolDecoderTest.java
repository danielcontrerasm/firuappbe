package com.example.pettracker.gps.protocol.v41;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class V41ProtocolDecoderTest {

    @Test
    void decodesBatteryPercentAdditionalField() {
        V41ProtocolDecoder decoder = new V41ProtocolDecoder(ZoneId.of("America/Bogota"));

        V41ProtocolDecoder.DecodeResult result = decoder.decode(
                Unpooled.wrappedBuffer(locationFrameWithBattery(87))
        );

        assertThat(result.getPosition()).isNotNull();
        assertThat(result.getPosition().getBattery()).isEqualTo(87);
        assertThat(result.getPosition().getAdditionalFields())
                .containsEntry("batteryPercentRaw", "87");
    }

    private byte[] locationFrameWithBattery(int batteryPercent) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeInt(body, 0); // alarm flags
        writeInt(body, 0x02); // status flags: GPS valid
        writeInt(body, 4_710_989); // latitude * 1_000_000
        writeInt(body, 74_072_580); // longitude * 1_000_000
        writeShort(body, 0); // altitude
        writeShort(body, 0); // speed
        writeShort(body, 0); // course
        body.writeBytes(new byte[] {0x26, 0x10, 0x04, 0x12, 0x34, 0x56});
        body.write(0xE1);
        body.write(0x01);
        body.write(batteryPercent);

        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeShort(payload, 0x0200);
        writeShort(payload, bodyBytes.length);
        payload.writeBytes(new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x90, 0x12});
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
