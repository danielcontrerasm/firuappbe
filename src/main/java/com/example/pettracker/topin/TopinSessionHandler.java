package com.example.pettracker.topin;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

/** Per-connection identity and protocol replies; does not write to your database. */
public final class TopinSessionHandler extends SimpleChannelInboundHandler<TopinPacket> {
    private static final System.Logger LOG = System.getLogger(TopinSessionHandler.class.getName());
    private final Predicate<String> knownImei;
    private final TopinReplyProfile profile;
    private final Clock clock;
    private final Duration maxFutureSkew;
    private final Duration maxPastAge;
    private final boolean syncTimeOnLogin;
    private String imei;
    private boolean closingAfterError;

    public TopinSessionHandler(Predicate<String> knownImei, TopinReplyProfile profile) {
        this(knownImei, profile, Clock.systemUTC(), Duration.ofMinutes(5), true);
    }

    public TopinSessionHandler(Predicate<String> knownImei, TopinReplyProfile profile, Clock clock,
                               Duration maxFutureSkew, boolean syncTimeOnLogin) {
        this(knownImei, profile, clock, maxFutureSkew, Duration.ofMinutes(10), syncTimeOnLogin);
    }

    public TopinSessionHandler(Predicate<String> knownImei, TopinReplyProfile profile, Clock clock,
                               Duration maxFutureSkew, Duration maxPastAge, boolean syncTimeOnLogin) {
        this.knownImei = Objects.requireNonNull(knownImei);
        this.profile = Objects.requireNonNull(profile);
        this.clock = Objects.requireNonNull(clock);
        this.maxFutureSkew = Objects.requireNonNull(maxFutureSkew);
        this.maxPastAge = Objects.requireNonNull(maxPastAge);
        if (maxFutureSkew.isNegative()) throw new IllegalArgumentException("Negative clock tolerance");
        if (maxPastAge.isNegative()) throw new IllegalArgumentException("Negative maximum position age");
        this.syncTimeOnLogin = syncTimeOnLogin;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TopinPacket packet) {
        if (!ctx.channel().isActive()) return;
        TopinMessage message = TopinParser.parse(packet);
        Instant receivedAt = clock.instant();
        if (message instanceof TopinMessage.Login login) {
            if ((imei != null && !imei.equals(login.imei())) || !knownImei.test(login.imei())) {
                LOG.log(System.Logger.Level.WARNING, "Topin login rejected remote=" + ctx.channel().remoteAddress());
                // Do not send 0x44: some firmware treats it as an instruction to stop uploading.
                ctx.close();
                return;
            }
            imei = login.imei();
            send(ctx, TopinReplies.acceptedLogin(login.declaredLength(), profile));
            if (syncTimeOnLogin) send(ctx, TopinReplies.utcTime(receivedAt));
        } else if (imei == null) {
            if (message instanceof TopinMessage.SettingsRequest || message instanceof TopinMessage.DeviceInfo
                    || message instanceof TopinMessage.SimInfo) {
                // Some firmware sends control/metadata before identification. Consume it without
                // assigning a device identity, publishing a position, or sending guessed settings.
                LOG.log(System.Logger.Level.DEBUG, "Topin pre-login control consumed; awaiting IMEI, type="
                        + Integer.toHexString(message.type()));
                return;
            }
            LOG.log(System.Logger.Level.WARNING, "Topin packet before login; closing connection");
            ctx.close();
            return;
        } else if (message instanceof TopinMessage.Gps gps) {
            if (gps.type() == 0x10 || gps.type() == 0x11) send(ctx, TopinReplies.gps(packet, profile));
        } else if (message instanceof TopinMessage.TimeRequest) {
            send(ctx, TopinReplies.utcTime(receivedAt));
        } else if (message instanceof TopinMessage.Status && profile == TopinReplyProfile.TRACCAR_COMPATIBLE) {
            send(ctx, packet.wireBytes());
        }

        boolean live = false;
        String note = "Not a position";
        if (message instanceof TopinMessage.Gps gps) {
            if (!gps.valid()) note = "GPS fix not marked valid";
            else if (gps.historical()) note = "Historical position";
            else if (gps.deviceTime().isAfter(receivedAt.plus(maxFutureSkew))) note = "Device timestamp is in the future";
            else if (gps.deviceTime().isBefore(receivedAt.minus(maxPastAge))) note = "Position is too old for live tracking";
            else { live = true; note = "GPS fix accepted; application must still check order and freshness"; }
        }
        ctx.fireChannelRead(new TopinDeviceEvent(imei, receivedAt, message, live, note));
    }

    private static void send(ChannelHandlerContext ctx, byte[] bytes) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (closingAfterError) return;
        closingAfterError = true;
        LOG.log(System.Logger.Level.WARNING, "Topin connection closed: " + cause.getMessage());
        ctx.close();
    }
}
