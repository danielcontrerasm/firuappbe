package com.example.pettracker.topin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;

/**
 * Framing for explicitly supported Topin packet profiles. New instance per channel.
 * Length conventions come from the manufacturer document and independent Traccar fixtures.
 * Never split binary payload on the first CRLF; CRLF can occur inside coordinates or time.
 * Unsupported profiles fail visibly instead of guessing a boundary or creating a false position.
 */
public final class TopinFrameDecoder extends ByteToMessageDecoder {
    private boolean failed;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (failed) {
            in.skipBytes(in.readableBytes());
            return;
        }
        if (in.readableBytes() < 4) return;
        int start = in.readerIndex();
        if (in.getUnsignedShort(start) != 0x7878) {
            throw failure("Expected Topin header 7878", in);
        }
        int length = in.getUnsignedByte(start + 2);
        int type = in.getUnsignedByte(start + 3);
        int size = frameSize(type, length);
        if (size < 0) {
            throw failure(String.format("Unsupported Topin profile type=%02x length=%02x", type, length), in);
        }
        if (in.readableBytes() < size) return;
        if (in.getUnsignedShort(start + size - 2) != 0x0d0a) {
            throw failure("Expected 0d0a at known frame boundary; expectedBytes=" + size, in);
        }
        byte[] payload = ByteBufUtil.getBytes(in, start + 4, size - 6);
        in.skipBytes(size);
        out.add(new TopinPacket(length, type, payload));
    }

    private static int frameSize(int type, int length) {
        return switch (type) {
            case 0x01 -> length == 0x0a || length == 0x0d ? 15 : -1;
            case 0x10, 0x11 -> switch (length) {
                case 0x12 -> 24;
                case 0x15 -> 27;
                default -> -1;
            };
            case 0x08 -> length == 1 ? 6 : length == 0x20 ? 37 : -1;
            case 0x09 -> length == 0x20 ? 37 : -1;
            case 0x13 -> switch (length) {
                case 0x06 -> 10;
                case 0x07 -> 11;
                case 0x0a -> 15;
                default -> -1;
            };
            case 0x14, 0x30, 0x57, 0x94, 0x99 -> length == 1 ? 6 : -1;
            // Captured G68P profile: length counts type + payload; maximum total 260 bytes.
            // Keep this rule specific to FC. Other Topin messages have different length rules.
            case 0xfc -> length >= 1 ? length + 5 : -1;
            default -> -1;
        };
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.isReadable()) decode(ctx, in, out);
        if (in.isReadable()) {
            throw failure("Connection ended with an incomplete Topin frame", in);
        }
    }

    private CorruptedFrameException failure(String message, ByteBuf in) {
        String prefix = ByteBufUtil.hexDump(in, in.readerIndex(), Math.min(in.readableBytes(), 64));
        int available = in.readableBytes();
        failed = true;
        // This is a fatal framing failure. Closing must not re-decode and report the same bytes.
        in.skipBytes(available);
        return new CorruptedFrameException(message + "; available=" + available + "; hexPrefix=" + prefix);
    }
}
