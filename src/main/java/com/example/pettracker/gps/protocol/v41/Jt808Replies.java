package com.example.pettracker.gps.protocol.v41;

import com.example.pettracker.gps.protocol.v41.V41ProtocolDecoder.DecodeResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Standard JT/T 808 registration and platform general responses. */
public final class Jt808Replies {
    private Jt808Replies() {}

    public static boolean requiresReply(int messageId) {
        return messageId == 0x0100 || messageId == 0x0102
                || messageId == 0x0002 || messageId == 0x0200;
    }

    public static byte[] response(DecodeResult request, int sequence, boolean knownTerminal) {
        if (!requiresReply(request.getMessageId())) {
            throw new IllegalArgumentException("No JT808 response for this message type");
        }

        boolean registration = request.getMessageId() == 0x0100;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeShort(body, request.getSequence());
        if (registration) {
            body.write(knownTerminal ? 0 : 4); // success / terminal not registered
            if (knownTerminal) {
                // A stable reconnect token; device admission uses the registered terminal ID.
                body.writeBytes(request.getTerminalId().getBytes(StandardCharsets.US_ASCII));
            }
        } else {
            writeShort(body, request.getMessageId());
            body.write(knownTerminal ? 0 : 1); // success / failure
        }

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeShort(payload, registration ? 0x8100 : 0x8001);
        boolean versioned = request.getProtocolVersion() != null;
        writeShort(payload, body.size() | (versioned ? 0x4000 : 0));
        if (versioned) payload.write(request.getProtocolVersion());
        String terminalId = request.getTerminalId();
        for (int i = 0; i < terminalId.length(); i += 2) {
            payload.write(((terminalId.charAt(i) - '0') << 4) | (terminalId.charAt(i + 1) - '0'));
        }
        writeShort(payload, sequence);
        payload.writeBytes(body.toByteArray());

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x7E);
        int checksum = 0;
        for (byte value : payload.toByteArray()) {
            checksum ^= value & 0xFF;
            writeEscaped(frame, value & 0xFF);
        }
        writeEscaped(frame, checksum);
        frame.write(0x7E);
        return frame.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeEscaped(ByteArrayOutputStream out, int value) {
        if (value == 0x7D || value == 0x7E) {
            out.write(0x7D);
            out.write(value == 0x7D ? 1 : 2);
        } else {
            out.write(value);
        }
    }
}
