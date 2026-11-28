package com.example.pettracker.topin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StandaloneTopinServerTest {
    @ParameterizedTest
    @CsvSource({
            "TRACCAR_COMPATIBLE, 78780a01010d0a, 78781210",
            "DOCUMENT_2017, 787801010d0a, 78780010"
    })
    void acknowledgesLivePositionInStandaloneMode(TopinReplyProfile profile, String loginReply, String gpsReplyHeader) {
        List<TopinDeviceEvent> events = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            StandaloneTopinServer.installPipeline(channel.pipeline(), Set.of("123456789012345"), profile, 0, events::add);
            channel.writeInbound(Unpooled.wrappedBuffer(HexFormat.of().parseHex("78780a010123456789012345010d0a")));
            assertThat(readReply(channel)).isEqualTo(loginReply);
            assertThat(readReply(channel)).startsWith("78780730").endsWith("0d0a");

            Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            byte[] payload = livePositionPayload(timestamp);
            channel.writeInbound(Unpooled.wrappedBuffer(new TopinPacket(0x12, 0x10, payload).wireBytes()));

            assertThat(events).hasSize(2);
            assertThat(events.getLast().eligibleForLiveLocation()).isTrue();
            assertThat(events.getLast().message()).isInstanceOfSatisfying(TopinMessage.Gps.class,
                    gps -> assertThat(gps.deviceTime()).isEqualTo(timestamp));
            assertThat(readReply(channel)).isEqualTo(gpsReplyHeader + HexFormat.of().formatHex(payload, 0, 6) + "0d0a");
            assertThat((Object) channel.readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] livePositionPayload(Instant timestamp) {
        var utc = timestamp.atOffset(ZoneOffset.UTC);
        return ByteBuffer.allocate(18)
                .put((byte) (utc.getYear() - 2000)).put((byte) utc.getMonthValue()).put((byte) utc.getDayOfMonth())
                .put((byte) utc.getHour()).put((byte) utc.getMinute()).put((byte) utc.getSecond())
                .put((byte) 6).putInt(4 * 1_800_000).putInt(74 * 1_800_000)
                .put((byte) 0).putShort((short) 0x1c00).array();
    }

    private static String readReply(EmbeddedChannel channel) {
        ByteBuf reply = channel.readOutbound();
        assertThat(reply).as("Topin protocol reply").isNotNull();
        try {
            return ByteBufUtil.hexDump(reply);
        } finally {
            reply.release();
        }
    }
}
