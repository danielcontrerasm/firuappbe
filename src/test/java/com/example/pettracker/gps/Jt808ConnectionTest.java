package com.example.pettracker.gps;

import com.example.pettracker.service.AppTimeService;
import com.example.pettracker.service.GpsIngestionService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Jt808ConnectionTest {
    private static final String TERMINAL = "013345678906";
    private static final String TERMINAL_2019 = "00000001234567890123";
    // Published registration example: https://www.traccar.org/identify-protocol/
    private static final String REGISTER =
            "7e01000021013345678906000f002c012f373031313142534a2d4d3742203030303030303001d4c1423838383838b47e";
    private static final String AUTH = "7e0102000c0133456789060010303133333435363738393036867e";
    private static final String HEARTBEAT = "7e000200000133456789067d017d029e7e";
    private static final String LOCATION =
            "7e0200001f0133456789060012000000000000000a0047e24d046a4204000000000000261105123456e10157af7e";
    private static final String HEARTBEAT_2019 = "7e0002400001000000012345678901230021c97e";
    private static final String HEARTBEAT_2019_REPLY =
            "7e800140050100000001234567890123000000210002004d7e";
    private static final String LOCATION_2019 =
            "7e0200401f01000000012345678901230022000000000000000a0047e24d046a4204000000000000261105123456e10157ea7e";

    @Test
    void legacyRegistrationAuthenticationHeartbeatAndLocationWorkOnSharedPort() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        when(ingestion.isKnownTerminalId(TERMINAL)).thenReturn(true);
        EmbeddedChannel channel = channel(ingestion);
        try {
            // Fragment the first packet, then coalesce three different message types.
            write(channel, REGISTER.substring(0, 10));
            assertThat((Object) channel.readOutbound()).isNull();
            write(channel, REGISTER.substring(10) + AUTH + HEARTBEAT + LOCATION);

            assertReply(channel, "7e8100000f0133456789060000000f00303133333435363738393036187e");
            assertReply(channel, "7e8001000501334567890600010010010200097e");
            // Both reserved bytes in the acknowledged request sequence must be escaped.
            assertReply(channel, "7e8001000501334567890600027d017d02000200187e");
            assertReply(channel, "7e8001000501334567890600030012020000087e");
            assertThat((Object) channel.readOutbound()).isNull();
            assertLocation(ingestion, TERMINAL);
            assertThat(channel.isActive()).isTrue();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void version2019PreservesPaddedIdentityAndVersionInReplies() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        when(ingestion.isKnownTerminalId(TERMINAL_2019)).thenReturn(true);
        EmbeddedChannel channel = channel(ingestion);
        try {
            // Extra delimiters and fragmented 2019 header must not become a corrupt frame.
            write(channel, "7e7e" + HEARTBEAT_2019.substring(0, 8));
            assertThat((Object) channel.readOutbound()).isNull();
            write(channel, HEARTBEAT_2019.substring(8) + LOCATION_2019);
            assertReply(channel, HEARTBEAT_2019_REPLY);
            assertReply(channel, "7e800140050100000001234567890123000100220200004f7e");
            assertLocation(ingestion, TERMINAL_2019);
            assertThat((Object) channel.readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void unknownTerminalGetsFailureRepliesAndNoLocationIsIngested() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        EmbeddedChannel channel = channel(ingestion);
        try {
            write(channel, REGISTER + LOCATION);
            assertReply(channel, "7e810000030133456789060000000f04167e");
            assertReply(channel, "7e80010005013345678906000100120200010b7e");
            verify(ingestion, times(2)).isKnownTerminalId(TERMINAL);
            verifyNoMoreInteractions(ingestion);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void replySequencesAreIndependentForConnectionsUsingTheSameHandler() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        when(ingestion.isKnownTerminalId(TERMINAL_2019)).thenReturn(true);
        GpsMessageHandler handler = new GpsMessageHandler(ingestion, new AppTimeService("America/Bogota"));
        EmbeddedChannel first = new EmbeddedChannel(new GpsProtocolDetector(handler, ingestion));
        EmbeddedChannel second = new EmbeddedChannel(new GpsProtocolDetector(handler, ingestion));
        try {
            write(first, HEARTBEAT_2019 + HEARTBEAT_2019);
            write(second, HEARTBEAT_2019);
            assertReply(first, HEARTBEAT_2019_REPLY);
            assertReply(first, "7e800140050100000001234567890123000100210002004c7e");
            assertReply(second, HEARTBEAT_2019_REPLY);
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7e000200000133456789067d017d029f7e", // incorrect checksum
            "7e0002000101334567890600019d7e", // declared body length mismatch
            "7e000204000133456789060001987e", // encryption
            "7e00022000013345678906000100020001bf7e", // subpackages need reassembly
            "7e0002000001334567890600017d039c7e", // invalid escape
            "7e0002000001334567890600017d7e" // incomplete escape
    })
    void malformedOrUnsupportedPacketsAreNotAcknowledgedOrIngested(String packet) {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        EmbeddedChannel channel = channel(ingestion);
        try {
            write(channel, packet);
            assertThat((Object) channel.readOutbound()).isNull();
            verifyNoInteractions(ingestion);
            // A bad packet must not prevent processing the next valid packet.
            when(ingestion.isKnownTerminalId(TERMINAL_2019)).thenReturn(true);
            write(channel, HEARTBEAT_2019);
            assertReply(channel, HEARTBEAT_2019_REPLY);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void deviceGeneralResponseDoesNotCreateAcknowledgementLoop() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        EmbeddedChannel channel = channel(ingestion);
        try {
            write(channel, "7e00010005013345678906000100008001001b7e");
            assertThat((Object) channel.readOutbound()).isNull();
            verifyNoInteractions(ingestion);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel channel(GpsIngestionService ingestion) {
        return new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, new AppTimeService("America/Bogota")), ingestion));
    }

    private static void write(EmbeddedChannel channel, String hex) {
        channel.writeInbound(Unpooled.wrappedBuffer(HexFormat.of().parseHex(hex)));
    }

    private static void assertReply(EmbeddedChannel channel, String expectedHex) {
        ByteBuf reply = channel.readOutbound();
        assertThat(reply).isNotNull();
        try {
            assertThat(ByteBufUtil.hexDump(reply)).isEqualTo(expectedHex);
        } finally {
            reply.release();
        }
    }

    private static void assertLocation(GpsIngestionService ingestion, String terminal) {
        verify(ingestion).processDecodedLocation(eq("V41"), eq(terminal), eq(4.710989), eq(-74.072580),
                eq(Instant.parse("2026-11-05T17:34:56Z")), eq(true), eq(87), isNull(), anyString(), anyString());
    }
}
