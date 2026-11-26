package com.example.pettracker.topin;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/** Install on a channel dedicated to Topin, before the business event handler. */
public final class TopinPipeline {
    private TopinPipeline() { }

    public static void install(ChannelPipeline pipeline, Predicate<String> knownImei, TopinReplyProfile profile) {
        install(pipeline, new TopinSessionHandler(knownImei, profile), 180);
    }

    public static void install(ChannelPipeline pipeline, Predicate<String> knownImei, TopinReplyProfile profile,
                               int readIdleSeconds) {
        install(pipeline, new TopinSessionHandler(knownImei, profile), readIdleSeconds);
    }

    public static void installAsync(ChannelPipeline pipeline,
                                   Function<String, CompletableFuture<Boolean>> knownImeiAsync,
                                   TopinReplyProfile profile) {
        install(pipeline, new TopinSessionHandler(knownImeiAsync, profile), 180);
    }

    public static void installAsync(ChannelPipeline pipeline,
                                   Function<String, CompletableFuture<Boolean>> knownImeiAsync,
                                   TopinReplyProfile profile, int readIdleSeconds) {
        install(pipeline, new TopinSessionHandler(knownImeiAsync, profile), readIdleSeconds);
    }

    private static void install(ChannelPipeline pipeline, TopinSessionHandler sessionHandler,
                                int readIdleSeconds) {
        if (readIdleSeconds < 0) throw new IllegalArgumentException("Negative read timeout");
        if (readIdleSeconds > 0) pipeline.addLast("topinReadTimeout", new ReadTimeoutHandler(readIdleSeconds));
        pipeline.addLast("topinFrames", new TopinFrameDecoder());
        pipeline.addLast("topinSession", sessionHandler);
    }
}
