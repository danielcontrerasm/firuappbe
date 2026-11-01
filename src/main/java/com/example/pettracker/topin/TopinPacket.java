package com.example.pettracker.topin;

import java.util.Objects;

/** One complete, structurally framed Topin packet. Length is a protocol field, not always a size. */
public record TopinPacket(int declaredLength, int type, byte[] payload) {
    public TopinPacket {
        if (declaredLength < 0 || declaredLength > 255 || type < 0 || type > 255) {
            throw new IllegalArgumentException("Length and type must fit an unsigned byte");
        }
        payload = Objects.requireNonNull(payload, "payload").clone();
    }

    @Override public byte[] payload() { return payload.clone(); }

    public byte[] wireBytes() {
        byte[] result = new byte[payload.length + 6];
        result[0] = 0x78;
        result[1] = 0x78;
        result[2] = (byte) declaredLength;
        result[3] = (byte) type;
        System.arraycopy(payload, 0, result, 4, payload.length);
        result[result.length - 2] = 0x0d;
        result[result.length - 1] = 0x0a;
        return result;
    }
}
