package com.example.pettracker.gps;

import com.example.pettracker.service.GpsIngestionService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GpsProtocolDetectorTest {
    @Test
    void fragmentedTopinLoginAndCoalescedHeartbeatReceiveReplies() {
        GpsIngestionService ingestion = mock(GpsIngestionService.class);
        when(ingestion.isKnownTerminalId("123456789012345")).thenReturn(true);
        EmbeddedChannel channel = new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, null, new Jt808TimeZones()), ingestion));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x78}));
            channel.writeInbound(Unpooled.wrappedBuffer(HexFormat.of().parseHex(
                    "780a010123456789012345010d0a787801080d0a")));
            verify(ingestion).isKnownTerminalId("123456789012345");
            assertThat(channel.isActive()).isTrue();
            ByteBuf reply = channel.readOutbound();
            assertThat(reply).isNotNull();
            reply.release();
            verifyNoMoreInteractions(ingestion);
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
}
