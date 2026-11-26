// com.example.pettracker.gps.GpsTcpServer
package com.example.pettracker.gps;

import com.example.pettracker.service.GpsIngestionService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Future;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GpsTcpServer {

    @Value("${gps.listener.port:5000}")
    private int port;

    @Value("${gps.listener.shutdown-timeout-seconds:10}")
    private int shutdownTimeoutSeconds;

    @Value("${gps.listener.worker-threads:0}")
    private int workerThreads;

    @Value("${gps.listener.legacy-read-idle-timeout-seconds:600}")
    private int legacyReadIdleTimeoutSeconds;

    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel serverChannel;
    private final ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private final GpsMessageHandler handler;

    private final GpsIngestionService ingestion;

    public GpsTcpServer(GpsMessageHandler handler, GpsIngestionService ingestion) {
        this.handler = handler;
        this.ingestion = ingestion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        if (workerThreads < 0) throw new IllegalArgumentException("GPS listener worker threads cannot be negative");
        if (legacyReadIdleTimeoutSeconds < 0) {
            throw new IllegalArgumentException("GPS legacy read idle timeout cannot be negative");
        }
        log.info("Starting GPS TCP listener on port {}", port);
        boss = new NioEventLoopGroup(1);
        workers = workerThreads == 0 ? new NioEventLoopGroup() : new NioEventLoopGroup(workerThreads);

        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, workers)
         .channel(NioServerSocketChannel.class)
         .childOption(ChannelOption.SO_KEEPALIVE, true)
         .childOption(ChannelOption.TCP_NODELAY, true)
         .childHandler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 clientChannels.add(ch);
                 log.info("Accepted GPS TCP channel remote={} local={}", ch.remoteAddress(), ch.localAddress());
                 ch.pipeline()
                   .addLast(new GpsProtocolDetector(handler, ingestion, legacyReadIdleTimeoutSeconds));
             }
         });

        try {
            ChannelFuture f = b.bind(port).sync();
            serverChannel = f.channel();
            log.info("GPS TCP listener started localAddress={}", serverChannel.localAddress());
        } catch (InterruptedException ex) {
            log.error("GPS TCP listener startup interrupted on port {}", port, ex);
            shutdownEventLoops(System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, shutdownTimeoutSeconds)));
            Thread.currentThread().interrupt();
            throw ex;
        } catch (RuntimeException ex) {
            log.error("GPS TCP listener failed to start on port {}", port, ex);
            shutdownEventLoops(System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, shutdownTimeoutSeconds)));
            throw ex;
        }
    }

    @PreDestroy
    public void shutdown() {
        int timeoutSeconds = Math.max(1, shutdownTimeoutSeconds);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        boolean stopped = true;
        if (serverChannel != null) stopped &= await(serverChannel.close(), "listening socket", deadline);
        stopped &= await(clientChannels.close(), "client channels", deadline);
        stopped &= shutdownEventLoops(deadline);
        if (stopped) log.info("GPS TCP listener stopped");
        else log.warn("GPS TCP listener shutdown did not finish before the {} second deadline", timeoutSeconds);
    }

    private boolean shutdownEventLoops(long deadline) {
        long shutdownTimeoutNanos = Math.max(1, remainingNanos(deadline));
        if (boss != null) {
            boss.shutdownGracefully(0, shutdownTimeoutNanos, TimeUnit.NANOSECONDS);
        }
        if (workers != null) {
            workers.shutdownGracefully(0, shutdownTimeoutNanos, TimeUnit.NANOSECONDS);
        }
        boolean terminated = true;
        if (boss != null) terminated &= await(boss.terminationFuture(), "boss event loop", deadline);
        if (workers != null) terminated &= await(workers.terminationFuture(), "worker event loops", deadline);
        return terminated;
    }

    private boolean await(Future<?> future, String resource, long deadline) {
        if (!future.awaitUninterruptibly(remainingNanos(deadline), TimeUnit.NANOSECONDS)) {
            log.warn("Timed out waiting for GPS TCP {} to close", resource);
            return false;
        }
        if (!future.isSuccess()) {
            log.warn("GPS TCP {} did not close cleanly: {}", resource, future.cause());
            return false;
        }
        return true;
    }

    private long remainingNanos(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }
}
