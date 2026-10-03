// com.example.pettracker.gps.GpsTcpServer
package com.example.pettracker.gps;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Slf4j
@Component
public class GpsTcpServer {

    @Value("${gps.listener.port:5000}")
    private int port;

    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel serverChannel;

    private final GpsMessageHandler handler;

    public GpsTcpServer(GpsMessageHandler handler) {
        this.handler = handler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        log.info("=== Starting GPS TCP Server ===");
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, workers)
         .channel(NioServerSocketChannel.class)
         .option(ChannelOption.SO_BACKLOG, 128)
         .childOption(ChannelOption.SO_KEEPALIVE, true)
         .childHandler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
             @Override
             protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
                 log.debug("New channel initialized: {}", ch.remoteAddress());
                 ch.pipeline()
                   .addLast(new LineBasedFrameDecoder(2048))
                   .addLast(new StringDecoder())
                   .addLast(new DebugChannelHandler())
                   .addLast(handler);
             }
         });

        ChannelFuture f = b.bind(port).sync();
        serverChannel = f.channel();
        log.info("GPS TCP listener started on port {}", port);
        log.info("GPS TCP Server is LISTENING and READY for connections");
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (serverChannel != null) serverChannel.close().sync();
        } catch (InterruptedException ignored) {}
        if (boss != null) boss.shutdownGracefully();
        if (workers != null) workers.shutdownGracefully();
        log.info("GPS TCP listener stopped");
    }

    /**
     * Debug handler to log channel lifecycle events
     */
    @Slf4j
    private static class DebugChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("*** CHANNEL ACTIVE *** Remote: {}, Local: {}", 
                ctx.channel().remoteAddress(), ctx.channel().localAddress());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("*** CHANNEL INACTIVE *** Remote: {}", ctx.channel().remoteAddress());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("*** CHANNEL EXCEPTION *** Remote: {} - Error: {}", 
                ctx.channel().remoteAddress(), cause.getMessage(), cause);
            super.exceptionCaught(ctx, cause);
        }
    }
}

