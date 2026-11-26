package com.example.pettracker.gps;

import com.example.pettracker.gps.protocol.v41.V41FrameDecoder;
import com.example.pettracker.service.GpsIngestionService;
import com.example.pettracker.topin.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.List;

/** Selects Topin or the shared V41/JT808/bracket ASCII decoder without consuming initial bytes. */
public final class GpsProtocolDetector extends ByteToMessageDecoder {
    private static final int DEFAULT_LEGACY_READ_IDLE_TIMEOUT_SECONDS = 600;
    private final GpsMessageHandler legacyHandler;
    private final GpsIngestionService ingestion;
    private final int legacyReadIdleTimeoutSeconds;

    public GpsProtocolDetector(GpsMessageHandler legacyHandler, GpsIngestionService ingestion) {
        this(legacyHandler, ingestion, DEFAULT_LEGACY_READ_IDLE_TIMEOUT_SECONDS);
    }

    public GpsProtocolDetector(GpsMessageHandler legacyHandler, GpsIngestionService ingestion,
                               int legacyReadIdleTimeoutSeconds) {
        if (legacyReadIdleTimeoutSeconds < 0) {
            throw new IllegalArgumentException("GPS legacy read idle timeout cannot be negative");
        }
        this.legacyHandler = legacyHandler;
        this.ingestion = ingestion;
        this.legacyReadIdleTimeoutSeconds = legacyReadIdleTimeoutSeconds;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) return;
        int first = in.getUnsignedByte(in.readerIndex());
        if (first == 0x78) {
            if (in.readableBytes() < 2) return;
            if (in.getUnsignedByte(in.readerIndex() + 1) != 0x78) {
                in.skipBytes(in.readableBytes());
                ctx.close();
                return;
            }
            TopinPipeline.installAsync(ctx.pipeline(), ingestion::isKnownTerminalIdAsync,
                    TopinReplyProfile.TRACCAR_COMPATIBLE);
            ctx.pipeline().addLast(new TopinLocationHandler(ingestion));
        } else {
            if (legacyReadIdleTimeoutSeconds > 0) {
                ctx.pipeline().addLast("gpsLegacyReadTimeout", new ReadTimeoutHandler(legacyReadIdleTimeoutSeconds));
            }
            ctx.pipeline().addLast(new V41FrameDecoder()).addLast(legacyHandler);
        }
        // Removing this decoder forwards its accumulated bytes to the selected decoder.
        ctx.pipeline().remove(this);
    }
}
