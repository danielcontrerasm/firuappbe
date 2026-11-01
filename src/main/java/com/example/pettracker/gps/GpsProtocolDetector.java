package com.example.pettracker.gps;

import com.example.pettracker.gps.protocol.v41.V41FrameDecoder;
import com.example.pettracker.service.GpsIngestionService;
import com.example.pettracker.topin.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

/** Selects a protocol once per connection without consuming the initial bytes. */
public final class GpsProtocolDetector extends ByteToMessageDecoder {
    private final GpsMessageHandler legacyHandler;
    private final GpsIngestionService ingestion;

    public GpsProtocolDetector(GpsMessageHandler legacyHandler, GpsIngestionService ingestion) {
        this.legacyHandler = legacyHandler;
        this.ingestion = ingestion;
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
            TopinPipeline.install(ctx.pipeline(), ingestion::isKnownTerminalId,
                    TopinReplyProfile.TRACCAR_COMPATIBLE);
            ctx.pipeline().addLast(new TopinLocationHandler(ingestion));
        } else {
            ctx.pipeline().addLast(new V41FrameDecoder()).addLast(legacyHandler);
        }
        // Removing this decoder forwards its accumulated bytes to the selected decoder.
        ctx.pipeline().remove(this);
    }
}
