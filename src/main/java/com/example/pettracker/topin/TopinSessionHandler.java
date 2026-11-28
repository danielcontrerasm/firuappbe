package com.example.pettracker.topin;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/** Per-connection identity and protocol replies; does not write to your database. */
public final class TopinSessionHandler extends SimpleChannelInboundHandler<TopinPacket> {
    private static final System.Logger LOG = System.getLogger(TopinSessionHandler.class.getName());
    private static final int MAX_PACKETS_DURING_LOGIN_LOOKUP = 64;
    private final Function<String, CompletableFuture<Boolean>> knownImeiAsync;
    private final TopinReplyProfile profile;
    private final Clock clock;
    private final Duration maxFutureSkew;
    private final Duration maxPastAge;
    private final boolean syncTimeOnLogin;
    private final Deque<PendingPacket> packetsDuringLoginLookup = new ArrayDeque<>();
    private String imei;
    private boolean closingAfterError;
    private boolean loginLookupPending;
    // Keep the original setting until every login in this batch has completed.
    private Boolean autoReadBeforeLoginLookup;

    public TopinSessionHandler(Predicate<String> knownImei, TopinReplyProfile profile) {
        this(knownImei, profile, Clock.systemUTC(), Duration.ofMinutes(5), true);
    }

    public TopinSessionHandler(Function<String, CompletableFuture<Boolean>> knownImeiAsync,
                               TopinReplyProfile profile) {
        this(knownImeiAsync, profile, Clock.systemUTC(), Duration.ofMinutes(5), true);
    }

    public TopinSessionHandler(Predicate<String> knownImei, TopinReplyProfile profile, Clock clock,
                               Duration maxFutureSkew, boolean syncTimeOnLogin) {
        this(knownImei, profile, clock, maxFutureSkew, Duration.ofMinutes(10), syncTimeOnLogin);
    }

    public TopinSessionHandler(Function<String, CompletableFuture<Boolean>> knownImeiAsync,
                               TopinReplyProfile profile, Clock clock,
                               Duration maxFutureSkew, boolean syncTimeOnLogin) {
        this(knownImeiAsync, profile, clock, maxFutureSkew, Duration.ofMinutes(10), syncTimeOnLogin);
    }

    public TopinSessionHandler(Predicate<String> knownImei, TopinReplyProfile profile, Clock clock,
                               Duration maxFutureSkew, Duration maxPastAge, boolean syncTimeOnLogin) {
        this(adapt(knownImei),
                profile, clock, maxFutureSkew, maxPastAge, syncTimeOnLogin);
    }

    public TopinSessionHandler(Function<String, CompletableFuture<Boolean>> knownImeiAsync,
                               TopinReplyProfile profile, Clock clock, Duration maxFutureSkew,
                               Duration maxPastAge, boolean syncTimeOnLogin) {
        this.knownImeiAsync = Objects.requireNonNull(knownImeiAsync);
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
        Instant receivedAt = clock.instant();
        if (loginLookupPending) {
            if (packetsDuringLoginLookup.size() >= MAX_PACKETS_DURING_LOGIN_LOOKUP) {
                LOG.log(System.Logger.Level.WARNING, "Too many Topin packets while login lookup is pending; closing connection");
                ctx.close();
                return;
            }
            packetsDuringLoginLookup.addLast(new PendingPacket(packet, receivedAt));
            return;
        }
        processPacket(ctx, packet, receivedAt);
    }

    private void processPacket(ChannelHandlerContext ctx, TopinPacket packet, Instant receivedAt) {
        if (!ctx.channel().isActive()) return;
        TopinMessage message = TopinParser.parse(packet);
        if (message instanceof TopinMessage.Login login) {
            if (imei != null && !imei.equals(login.imei())) {
                rejectLogin(ctx);
                return;
            }
            beginLoginLookup(ctx, login, receivedAt);
            return;
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
        } else if (message instanceof TopinMessage.TimeRequest) {
            send(ctx, TopinReplies.utcTime(receivedAt));
        } else if (message instanceof TopinMessage.Status && profile == TopinReplyProfile.TRACCAR_COMPATIBLE) {
            send(ctx, packet.wireBytes());
        }

        boolean live = false;
        String note = "Not a position";
        byte[] acknowledgementAfterIngestion = null;
        if (message instanceof TopinMessage.Gps gps) {
            if (!gps.valid()) note = "GPS fix not marked valid";
            else if (gps.historical()) note = "Historical position";
            else if (gps.deviceTime().isAfter(receivedAt.plus(maxFutureSkew))) note = "Device timestamp is in the future";
            else if (gps.deviceTime().isBefore(receivedAt.minus(maxPastAge))) note = "Position is too old for live tracking";
            else { live = true; note = "GPS fix accepted; application must still check order and freshness"; }
            if (gps.type() == 0x10 || gps.type() == 0x11) {
                byte[] acknowledgement = TopinReplies.gps(packet, profile);
                if (live) acknowledgementAfterIngestion = acknowledgement;
                else send(ctx, acknowledgement);
            }
        }
        ctx.fireChannelRead(new TopinDeviceEvent(imei, receivedAt, message, live, note,
                acknowledgementAfterIngestion));
    }

    private void beginLoginLookup(ChannelHandlerContext ctx, TopinMessage.Login login, Instant receivedAt) {
        loginLookupPending = true;
        if (autoReadBeforeLoginLookup == null) {
            autoReadBeforeLoginLookup = ctx.channel().config().isAutoRead();
            ctx.channel().config().setAutoRead(false);
        }

        CompletableFuture<Boolean> lookup;
        try {
            lookup = Objects.requireNonNull(knownImeiAsync.apply(login.imei()), "Terminal lookup returned null");
        } catch (RuntimeException ex) {
            finishLoginLookup(ctx, login, receivedAt, false, ex);
            return;
        }
        lookup.whenComplete((known, error) -> runOnEventLoop(ctx,
                () -> finishLoginLookup(ctx, login, receivedAt, Boolean.TRUE.equals(known), error)));
    }

    private void finishLoginLookup(ChannelHandlerContext ctx, TopinMessage.Login login,
                                   Instant receivedAt, boolean known, Throwable error) {
        if (!ctx.channel().isActive()) return;
        if (error != null || !known) {
            if (error != null) {
                LOG.log(System.Logger.Level.WARNING, "Topin login lookup failed remote="
                        + ctx.channel().remoteAddress() + ": " + error.getMessage());
            } else {
                LOG.log(System.Logger.Level.WARNING, "Topin login rejected remote=" + ctx.channel().remoteAddress());
            }
            rejectLogin(ctx);
            return;
        }

        loginLookupPending = false;
        imei = login.imei();
        send(ctx, TopinReplies.acceptedLogin(login.declaredLength(), profile));
        if (syncTimeOnLogin) send(ctx, TopinReplies.utcTime(clock.instant()));
        ctx.fireChannelRead(new TopinDeviceEvent(imei, receivedAt, login, false,
                "Topin login accepted", null));

        while (ctx.channel().isActive() && !loginLookupPending && !packetsDuringLoginLookup.isEmpty()) {
            PendingPacket pending = packetsDuringLoginLookup.removeFirst();
            processPacket(ctx, pending.packet(), pending.receivedAt());
        }
        if (ctx.channel().isActive() && !loginLookupPending && autoReadBeforeLoginLookup != null) {
            boolean originalAutoRead = autoReadBeforeLoginLookup;
            autoReadBeforeLoginLookup = null;
            ctx.channel().config().setAutoRead(originalAutoRead);
        }
    }

    private void rejectLogin(ChannelHandlerContext ctx) {
        loginLookupPending = false;
        autoReadBeforeLoginLookup = null;
        packetsDuringLoginLookup.clear();
        // Do not send 0x44: some firmware treats it as an instruction to stop uploading.
        ctx.close();
    }

    private void runOnEventLoop(ChannelHandlerContext ctx, Runnable action) {
        if (ctx.executor().inEventLoop()) action.run();
        else ctx.executor().execute(action);
    }

    private static Function<String, CompletableFuture<Boolean>> adapt(Predicate<String> knownImei) {
        Objects.requireNonNull(knownImei);
        return imei -> CompletableFuture.completedFuture(knownImei.test(imei));
    }

    private record PendingPacket(TopinPacket packet, Instant receivedAt) { }

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
