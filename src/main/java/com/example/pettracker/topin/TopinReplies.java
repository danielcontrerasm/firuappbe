package com.example.pettracker.topin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

/** Server-to-device binary messages. They are TCP bytes, not SMS commands or ASCII hex. */
public final class TopinReplies {
    private TopinReplies() { }

    public static byte[] acceptedLogin(int receivedLength, TopinReplyProfile profile) {
        return profile == TopinReplyProfile.TRACCAR_COMPATIBLE
                ? new TopinPacket(receivedLength, 0x01, new byte[]{0x01}).wireBytes()
                : new TopinPacket(0x01, 0x01, new byte[0]).wireBytes();
    }

    public static byte[] gps(TopinPacket packet, TopinReplyProfile profile) {
        if ((packet.type() != 0x10 && packet.type() != 0x11) || packet.payload().length < 6) {
            throw new IllegalArgumentException("GPS reply requires a complete 10/11 packet");
        }
        int length = profile == TopinReplyProfile.TRACCAR_COMPATIBLE ? packet.declaredLength() : 0;
        return new TopinPacket(length, packet.type(), Arrays.copyOf(packet.payload(), 6)).wireBytes();
    }

    public static byte[] utcTime(Instant time) {
        var utc = time.atOffset(ZoneOffset.UTC);
        int year = utc.getYear();
        if (year < 0 || year > 65535) throw new IllegalArgumentException("UTC year outside protocol range");
        byte[] body = {(byte) (year >>> 8), (byte) year, (byte) utc.getMonthValue(),
                (byte) utc.getDayOfMonth(), (byte) utc.getHour(), (byte) utc.getMinute(), (byte) utc.getSecond()};
        return new TopinPacket(7, 0x30, body).wireBytes();
    }
}
