package com.example.pettracker.gps.protocol.v41;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class V41FrameDecoder extends ByteToMessageDecoder {

    private static final int FRAME_FLAG = 0x7E;
    private static final int MAX_HEX_LOG_LENGTH = 256;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            int readerIndex = in.readerIndex();
            int writerIndex = in.writerIndex();
            int startIndex = indexOf(in, readerIndex, writerIndex, FRAME_FLAG);

            if (startIndex < 0) {
                log.warn("V41 frame decoder discarding bytes without start flag remote={} bytes={} hex={}",
                        ctx.channel().remoteAddress(),
                        in.readableBytes(),
                        truncateHex(ByteBufUtil.hexDump(in, readerIndex, in.readableBytes())));
                in.skipBytes(in.readableBytes());
                return;
            }

            if (startIndex > readerIndex) {
                int discardedBytes = startIndex - readerIndex;
                log.warn("V41 frame decoder discarding bytes before frame flag remote={} bytes={} hex={}",
                        ctx.channel().remoteAddress(),
                        discardedBytes,
                        truncateHex(ByteBufUtil.hexDump(in, readerIndex, discardedBytes)));
                in.skipBytes(discardedBytes);
            }

            int frameStart = in.readerIndex();
            int frameEnd = indexOf(in, frameStart + 1, in.writerIndex(), FRAME_FLAG);

            if (frameEnd < 0) {
                log.debug("V41 frame decoder waiting for end flag remote={} bufferedBytes={}",
                        ctx.channel().remoteAddress(), in.readableBytes());
                return;
            }

            int frameLength = frameEnd - frameStart + 1;
            if (frameLength <= 2) {
                log.debug("V41 frame decoder skipping empty frame remote={}", ctx.channel().remoteAddress());
                in.skipBytes(frameLength);
                continue;
            }

            ByteBuf frame = in.readRetainedSlice(frameLength);
            log.info("V41 frame decoder emitted frame remote={} bytes={} hex={}",
                    ctx.channel().remoteAddress(),
                    frameLength,
                    truncateHex(ByteBufUtil.hexDump(frame, frame.readerIndex(), frame.readableBytes())));
            out.add(frame);
        }
    }

    private int indexOf(ByteBuf buffer, int fromIndex, int toIndex, int value) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (buffer.getUnsignedByte(i) == value) {
                return i;
            }
        }
        return -1;
    }

    private String truncateHex(String hex) {
        if (hex == null || hex.length() <= MAX_HEX_LOG_LENGTH) {
            return hex;
        }
        return hex.substring(0, MAX_HEX_LOG_LENGTH) + "...(truncated," + hex.length() + " hex chars)";
    }
}
