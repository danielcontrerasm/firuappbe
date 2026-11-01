package com.example.pettracker.gps;

import com.example.pettracker.service.GpsIngestionService;
import com.example.pettracker.topin.TopinDeviceEvent;
import com.example.pettracker.topin.TopinMessage;
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
                    String.valueOf(ctx.channel().remoteAddress()), event.qualityNote());
        }
    }
}
