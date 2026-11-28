package com.example.pettracker.gps;

import com.example.pettracker.service.GpsIngestionResult;
import com.example.pettracker.service.GpsIngestionService;
import com.example.pettracker.topin.TopinPacket;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GpsProtocolDetectorTest {
    private static final String TOPIN_IMEI = "123456789012345";
    private static final String TOPIN_LOGIN = "78780a010123456789012345010d0a";
    private static final String TOPIN_STATUS = "787806133c0000000d0a";

    @Test
    void fragmentedTopinLoginWaitsForLookupAndReplaysCoalescedPackets() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        CompletableFuture<Boolean> lookup = new CompletableFuture<>();
        when(ingestion.isKnownTerminalIdAsync(TOPIN_IMEI)).thenReturn(lookup);
        EmbeddedChannel channel = channel(ingestion);
        try {
            write(channel, TOPIN_LOGIN.substring(0, 2));
            assertThat((Object) channel.readOutbound()).isNull();
            write(channel, TOPIN_LOGIN.substring(2) + "787801080d0a" + TOPIN_STATUS);
            verify(ingestion).isKnownTerminalIdAsync(TOPIN_IMEI);
            assertThat(channel.config().isAutoRead()).isFalse();
            assertThat((Object) channel.readOutbound()).isNull();

            lookup.complete(true);
            channel.runPendingTasks();
            assertTopinLoginReplies(channel);
            assertThat(readReply(channel)).isEqualTo(TOPIN_STATUS);
            assertThat((Object) channel.readOutbound()).isNull();
            assertThat(channel.isActive()).isTrue();
            assertThat(channel.config().isAutoRead()).isTrue();
            verifyNoMoreInteractions(ingestion);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void restoresOriginalAutoReadAfterTwoQueuedTopinLogins(boolean originalAutoRead) {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        CompletableFuture<Boolean> firstLookup = new CompletableFuture<>();
        CompletableFuture<Boolean> secondLookup = new CompletableFuture<>();
        when(ingestion.isKnownTerminalIdAsync(TOPIN_IMEI)).thenReturn(firstLookup, secondLookup);
        EmbeddedChannel channel = channel(ingestion);
        try {
            channel.config().setAutoRead(originalAutoRead);
            write(channel, TOPIN_LOGIN + TOPIN_LOGIN + TOPIN_STATUS);
            verify(ingestion).isKnownTerminalIdAsync(TOPIN_IMEI);
            assertThat(channel.config().isAutoRead()).isFalse();
            assertThat((Object) channel.readOutbound()).isNull();

            firstLookup.complete(true);
            channel.runPendingTasks();
            verify(ingestion, times(2)).isKnownTerminalIdAsync(TOPIN_IMEI);
            assertTopinLoginReplies(channel);
            assertThat(channel.config().isAutoRead()).isFalse();
            assertThat((Object) channel.readOutbound()).isNull();

            secondLookup.complete(true);
            channel.runPendingTasks();
            assertTopinLoginReplies(channel);
            assertThat(readReply(channel)).isEqualTo(TOPIN_STATUS);
            assertThat(channel.isActive()).isTrue();
            assertThat(channel.config().isAutoRead()).isEqualTo(originalAutoRead);
            assertThat((Object) channel.readOutbound()).isNull();

            write(channel, TOPIN_STATUS);
            assertThat(readReply(channel)).isEqualTo(TOPIN_STATUS);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void unknownTopinTerminalIsClosedWithoutAnAcceptedLoginReply() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        CompletableFuture<Boolean> lookup = new CompletableFuture<>();
        when(ingestion.isKnownTerminalIdAsync(TOPIN_IMEI)).thenReturn(lookup);
        EmbeddedChannel channel = channel(ingestion);
        try {
            write(channel, TOPIN_LOGIN);
            assertThat((Object) channel.readOutbound()).isNull();
            lookup.complete(false);
            channel.runPendingTasks();
            assertThat(channel.isActive()).isFalse();
            assertThat((Object) channel.readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @EnumSource(value = GpsIngestionResult.class, names = {"PERSISTED", "REJECTED", "FAILED"})
    void topinGpsReplyDependsOnAsyncIngestionOutcome(GpsIngestionResult outcome) {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        when(ingestion.isKnownTerminalIdAsync(TOPIN_IMEI)).thenReturn(CompletableFuture.completedFuture(true));
        CompletableFuture<GpsIngestionResult> persistence = new CompletableFuture<>();
        Instant time = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(ingestion.processDecodedLocation(eq("TOPIN"), eq(TOPIN_IMEI), eq(4.0), eq(-74.0),
                eq(time), eq(true), isNull(), isNull(), anyString(), anyString())).thenReturn(persistence);
        byte[] payload = topinGpsPayload(time);
        EmbeddedChannel channel = channel(ingestion);
        try {
            write(channel, TOPIN_LOGIN);
            assertTopinLoginReplies(channel);
            channel.writeInbound(Unpooled.wrappedBuffer(new TopinPacket(0x12, 0x10, payload).wireBytes()));
            verify(ingestion).processDecodedLocation(eq("TOPIN"), eq(TOPIN_IMEI), eq(4.0), eq(-74.0),
                    eq(time), eq(true), isNull(), isNull(), anyString(), anyString());
            assertThat((Object) channel.readOutbound()).isNull();

            persistence.complete(outcome);
            channel.runPendingTasks();
            if (outcome != GpsIngestionResult.FAILED) {
                String timestampBytes = HexFormat.of().formatHex(payload, 0, 6);
                assertThat(readReply(channel)).isEqualTo("78781210" + timestampBytes + "0d0a");
            }
            assertThat((Object) channel.readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void asciiTrafficStillReachesExistingHandler() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        EmbeddedChannel channel = new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, null, new Jt808TimeZones()), ingestion));
        try {
            channel.writeInbound(Unpooled.copiedBuffer("[SG*123*0002*LK]", java.nio.charset.StandardCharsets.US_ASCII));
            ByteBuf reply = channel.readOutbound();
            assertThat(reply).isNotNull();
            reply.release();
            verifyNoInteractions(ingestion);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel channel(GpsIngestionService ingestion) {
        return new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, null, new Jt808TimeZones()), ingestion));
    }

    private static byte[] topinGpsPayload(Instant timestamp) {
        var utc = timestamp.atOffset(ZoneOffset.UTC);
        return ByteBuffer.allocate(18)
                .put((byte) (utc.getYear() - 2000)).put((byte) utc.getMonthValue()).put((byte) utc.getDayOfMonth())
                .put((byte) utc.getHour()).put((byte) utc.getMinute()).put((byte) utc.getSecond())
                .put((byte) 6).putInt(4 * 1_800_000).putInt(74 * 1_800_000)
                .put((byte) 0).putShort((short) 0x1c00).array();
    }

    private static void write(EmbeddedChannel channel, String hex) {
        channel.writeInbound(Unpooled.wrappedBuffer(HexFormat.of().parseHex(hex)));
    }

    private static void assertTopinLoginReplies(EmbeddedChannel channel) {
        assertThat(readReply(channel)).isEqualTo("78780a01010d0a");
        assertThat(readReply(channel)).startsWith("78780730").endsWith("0d0a").hasSize(26);
    }

    private static String readReply(EmbeddedChannel channel) {
        ByteBuf reply = channel.readOutbound();
        assertThat(reply).isNotNull();
        try {
            return ByteBufUtil.hexDump(reply);
        } finally {
            reply.release();
        }
    }
}
