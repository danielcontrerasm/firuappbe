package com.example.pettracker.topin;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone diagnostic listener, disabled unless TOPIN_STANDALONE_ENABLED=true.
 * Events are printed; persistence belongs to the application.
 */
public final class StandaloneTopinServer {
    private StandaloneTopinServer() { }

    public static void main(String[] args) throws InterruptedException {
        if (!Boolean.parseBoolean(System.getenv("TOPIN_STANDALONE_ENABLED"))) {
            System.out.println("Standalone Topin listener is disabled. Start PettrackerApplication for GPS ingestion.");
            return;
        }
        String bind = System.getenv().getOrDefault("TOPIN_BIND", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("TOPIN_PORT", "5001"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("TOPIN_PORT must be 1..65535");
        Set<String> devices = Arrays.stream(System.getenv().getOrDefault("TOPIN_ALLOWED_IMEIS", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (devices.isEmpty() || devices.stream().anyMatch(s -> !s.matches("[0-9]{15}"))) {
            throw new IllegalArgumentException("Set TOPIN_ALLOWED_IMEIS to the allowed 15-digit IMEIs, comma-separated");
        }
        TopinReplyProfile profile = TopinReplyProfile.valueOf(
                System.getenv().getOrDefault("TOPIN_REPLY_PROFILE", "TRACCAR_COMPATIBLE"));
        boolean trace = Boolean.parseBoolean(System.getenv().getOrDefault("TOPIN_TRACE", "false"));
        int readIdleSeconds = Integer.parseInt(System.getenv().getOrDefault("TOPIN_READ_IDLE_SECONDS", "180"));
        if (readIdleSeconds < 0) throw new IllegalArgumentException("TOPIN_READ_IDLE_SECONDS cannot be negative");
        var boss = new NioEventLoopGroup(1);
        var workers = new NioEventLoopGroup(2);
        try {
            Channel server = new ServerBootstrap().group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel ch) {
                            if (trace) ch.pipeline().addLast("rawTopin", new LoggingHandler("gps.topin.raw", LogLevel.INFO));
                            TopinPipeline.install(ch.pipeline(), devices::contains, profile, readIdleSeconds);
                            ch.pipeline().addLast("events", new SimpleChannelInboundHandler<TopinDeviceEvent>() {
                                @Override protected void channelRead0(ChannelHandlerContext ctx, TopinDeviceEvent event) {
                                    System.out.println(event);
                                }
                            });
                        }
                    }).bind(bind, port).sync().channel();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "topin-shutdown"));
            System.out.printf("Topin listening on %s:%d profile=%s allowedDevices=%d%n", bind, port, profile, devices.size());
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            workers.shutdownGracefully().sync();
        }
    }
}
