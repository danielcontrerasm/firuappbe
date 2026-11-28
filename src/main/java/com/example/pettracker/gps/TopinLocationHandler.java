package com.example.pettracker.gps;

import com.example.pettracker.service.GpsIngestionResult;
import com.example.pettracker.service.GpsIngestionService;
import com.example.pettracker.topin.TopinDeviceEvent;
import com.example.pettracker.topin.TopinMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/** Per-connection adapter from Topin events to shared persistence and geofencing. */
final class TopinLocationHandler extends SimpleChannelInboundHandler<TopinDeviceEvent> {
    private final GpsIngestionService ingestion;
    private Integer batteryPercent;

    TopinLocationHandler(GpsIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TopinDeviceEvent event) {
        if (event.message() instanceof TopinMessage.Status status) {
            batteryPercent = status.batteryPercent();
        }
        if (event.eligibleForLiveLocation() && event.message() instanceof TopinMessage.Gps gps) {
            ingestion.processDecodedLocation("TOPIN", event.imei(), gps.latitude(), gps.longitude(),
                    gps.deviceTime(), gps.valid(), batteryPercent, null,
                    String.valueOf(ctx.channel().remoteAddress()), event.qualityNote())
                    .whenComplete((result, error) -> ctx.executor().execute(() -> {
                        if (!ctx.channel().isActive()) return;
                        if (error != null || (result != GpsIngestionResult.PERSISTED
                                && result != GpsIngestionResult.REJECTED)) {
                            return; // No positive device acknowledgement; allow a tracker retry.
                        }
                        byte[] acknowledgement = event.acknowledgementAfterIngestion();
                        if (acknowledgement != null) ctx.writeAndFlush(Unpooled.wrappedBuffer(acknowledgement));
                    }));
        }
    }
}
