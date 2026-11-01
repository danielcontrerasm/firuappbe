package com.example.pettracker.topin;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.function.Predicate;

/** Install on a channel dedicated to Topin, before the business event handler. */
public final class TopinPipeline {
    private TopinPipeline() { }

    public static void install(ChannelPipeline pipeline, Predicate<String> knownImei, TopinReplyProfile profile) {
        install(pipeline, knownImei, profile, 180);
    }

    public static void install(ChannelPipeline pipeline, Predicate<String> knownImei, TopinReplyProfile profile,
                               int readIdleSeconds) {
        if (readIdleSeconds < 0) throw new IllegalArgumentException("Negative read timeout");
        if (readIdleSeconds > 0) pipeline.addLast("topinReadTimeout", new ReadTimeoutHandler(readIdleSeconds));
        pipeline.addLast("topinFrames", new TopinFrameDecoder());
        pipeline.addLast("topinSession", new TopinSessionHandler(knownImei, profile));
    }
}
