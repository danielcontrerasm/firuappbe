// com.example.pettracker.gps.GpsTcpServer
package com.example.pettracker.gps;

import com.example.pettracker.gps.protocol.v41.V41FrameDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
        log.info("Starting GPS TCP listener on port {}", port);
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, workers)
         .channel(NioServerSocketChannel.class)
         .childHandler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
             @Override
             protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
                 log.info("Accepted GPS TCP channel remote={} local={}", ch.remoteAddress(), ch.localAddress());
                 ch.pipeline()
                   .addLast(new V41FrameDecoder())
                   .addLast(handler);
             }
         });

        try {
            ChannelFuture f = b.bind(port).sync();
            serverChannel = f.channel();
            log.info("GPS TCP listener started localAddress={}", serverChannel.localAddress());
        } catch (InterruptedException ex) {
            log.error("GPS TCP listener startup interrupted on port {}", port, ex);
            shutdownEventLoops();
            Thread.currentThread().interrupt();
            throw ex;
        } catch (RuntimeException ex) {
            log.error("GPS TCP listener failed to start on port {}", port, ex);
            shutdownEventLoops();
            throw ex;
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (serverChannel != null) serverChannel.close().sync();
        } catch (InterruptedException ignored) {}
        shutdownEventLoops();
        log.info("GPS TCP listener stopped");
    }

    private void shutdownEventLoops() {
        if (boss != null) boss.shutdownGracefully();
        if (workers != null) workers.shutdownGracefully();
    }
}
