package com.example.pettracker.gps.protocol.v41;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class V41FrameDecoder extends ByteToMessageDecoder {

    private static final int V41_FRAME_FLAG = 0x7E;
    private static final int ASCII_FRAME_START = '[';
    private static final int ASCII_FRAME_END = ']';
    private static final int MAX_HEX_LOG_LENGTH = 256;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            int readerIndex = in.readerIndex();
            int writerIndex = in.writerIndex();
            int v41StartIndex = indexOf(in, readerIndex, writerIndex, V41_FRAME_FLAG);
            int asciiStartIndex = indexOf(in, readerIndex, writerIndex, ASCII_FRAME_START);
            int startIndex = nearestStart(v41StartIndex, asciiStartIndex);

            if (startIndex < 0) {
                log.warn("GPS frame decoder discarding bytes without supported start flag remote={} bytes={} hex={}",
                        ctx.channel().remoteAddress(),
                        in.readableBytes(),
                        truncateHex(ByteBufUtil.hexDump(in, readerIndex, in.readableBytes())));
                in.skipBytes(in.readableBytes());
                return;
            }

            if (startIndex > readerIndex) {
                int discardedBytes = startIndex - readerIndex;
                log.warn("GPS frame decoder discarding bytes before frame flag remote={} bytes={} hex={}",
                        ctx.channel().remoteAddress(),
                        discardedBytes,
                        truncateHex(ByteBufUtil.hexDump(in, readerIndex, discardedBytes)));
                in.skipBytes(discardedBytes);
            }

            int frameStart = in.readerIndex();
            int startByte = in.getUnsignedByte(frameStart);
            int endByte = startByte == V41_FRAME_FLAG ? V41_FRAME_FLAG : ASCII_FRAME_END;
            int frameEnd = indexOf(in, frameStart + 1, in.writerIndex(), endByte);

            if (frameEnd < 0) {
                log.debug("GPS frame decoder waiting for end flag remote={} startByte=0x{} endByte=0x{} bufferedBytes={}",
                        ctx.channel().remoteAddress(),
                        String.format("%02X", startByte),
                        String.format("%02X", endByte),
                        in.readableBytes());
                return;
            }

            if (startByte == V41_FRAME_FLAG && frameEnd == frameStart + 1) {
                int nextV41End = indexOf(in, frameEnd + 1, in.writerIndex(), V41_FRAME_FLAG);
                if (nextV41End < 0) {
                    log.debug("GPS frame decoder skipping repeated V41 flag and waiting remote={} bufferedBytes={}",
                        ctx.channel().remoteAddress(), in.readableBytes());
                    in.skipBytes(1);
                    return;
                }
                frameEnd = nextV41End;
            }

            int frameLength = frameEnd - frameStart + 1;
            if (frameLength <= 2) {
                log.debug("GPS frame decoder skipping empty frame remote={} startByte=0x{}",
                        ctx.channel().remoteAddress(), String.format("%02X", startByte));
                in.skipBytes(frameLength);
                continue;
            }

            ByteBuf frame = in.readRetainedSlice(frameLength);
            log.info("GPS frame decoder emitted frame remote={} protocol={} bytes={} hex={}",
                    ctx.channel().remoteAddress(),
                    startByte == V41_FRAME_FLAG ? "V41" : "ASCII_BRACKET",
                    frameLength,
                    truncateHex(ByteBufUtil.hexDump(frame, frame.readerIndex(), frame.readableBytes())));
            out.add(frame);
        }
    }

    private int nearestStart(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
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
