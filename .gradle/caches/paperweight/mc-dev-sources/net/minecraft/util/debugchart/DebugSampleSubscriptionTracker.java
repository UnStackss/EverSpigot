package net.minecraft.util.debugchart;

import com.google.common.collect.Maps;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

public class DebugSampleSubscriptionTracker {
    public static final int STOP_SENDING_AFTER_TICKS = 200;
    public static final int STOP_SENDING_AFTER_MS = 10000;
    private final PlayerList playerList;
    private final EnumMap<RemoteDebugSampleType, Map<ServerPlayer, DebugSampleSubscriptionTracker.SubscriptionStartedAt>> subscriptions;
    private final Queue<DebugSampleSubscriptionTracker.SubscriptionRequest> subscriptionRequestQueue = new LinkedList<>();

    public DebugSampleSubscriptionTracker(PlayerList playerManager) {
        this.playerList = playerManager;
        this.subscriptions = new EnumMap<>(RemoteDebugSampleType.class);

        for (RemoteDebugSampleType remoteDebugSampleType : RemoteDebugSampleType.values()) {
            this.subscriptions.put(remoteDebugSampleType, Maps.newHashMap());
        }
    }

    public boolean shouldLogSamples(RemoteDebugSampleType type) {
        return !this.subscriptions.get(type).isEmpty();
    }

    public void broadcast(ClientboundDebugSamplePacket packet) {
        for (ServerPlayer serverPlayer : this.subscriptions.get(packet.debugSampleType()).keySet()) {
            serverPlayer.connection.send(packet);
        }
    }

    public void subscribe(ServerPlayer player, RemoteDebugSampleType type) {
        if (this.playerList.isOp(player.getGameProfile())) {
            this.subscriptionRequestQueue.add(new DebugSampleSubscriptionTracker.SubscriptionRequest(player, type));
        }
    }

    public void tick(int tick) {
        long l = Util.getMillis();
        this.handleSubscriptions(l, tick);
        this.handleUnsubscriptions(l, tick);
    }

    private void handleSubscriptions(long time, int tick) {
        for (DebugSampleSubscriptionTracker.SubscriptionRequest subscriptionRequest : this.subscriptionRequestQueue) {
            this.subscriptions
                .get(subscriptionRequest.sampleType())
                .put(subscriptionRequest.player(), new DebugSampleSubscriptionTracker.SubscriptionStartedAt(time, tick));
        }
    }

    private void handleUnsubscriptions(long measuringTimeMs, int tick) {
        for (Map<ServerPlayer, DebugSampleSubscriptionTracker.SubscriptionStartedAt> map : this.subscriptions.values()) {
            map.entrySet().removeIf(entry -> {
                boolean bl = !this.playerList.isOp(entry.getKey().getGameProfile());
                DebugSampleSubscriptionTracker.SubscriptionStartedAt subscriptionStartedAt = entry.getValue();
                return bl || tick > subscriptionStartedAt.tick() + 200 && measuringTimeMs > subscriptionStartedAt.millis() + 10000L;
            });
        }
    }

    static record SubscriptionRequest(ServerPlayer player, RemoteDebugSampleType sampleType) {
    }

    static record SubscriptionStartedAt(long millis, int tick) {
    }
}
