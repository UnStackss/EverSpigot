package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

// CraftBukkit start
import io.netty.buffer.ByteBuf;
import java.util.concurrent.ExecutionException;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener, CraftPlayer.TransferCookieConnection {

    @Override
    public boolean isTransferred() {
        return this.transferred;
    }

    @Override
    public ConnectionProtocol getProtocol() {
        return this.protocol();
    }

    @Override
    public void sendPacket(Packet<?> packet) {
        this.send(packet);
    }
    // CraftBukkit end
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final int CLOSED_LISTENER_TIMEOUT = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final MinecraftServer server;
    public final Connection connection; // Paper
    private final boolean transferred;
    private long keepAliveTime = Util.getMillis(); // Paper
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private long closedListenerTime;
    private boolean closed = false;
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;
    public final java.util.Map<java.util.UUID, net.kyori.adventure.resource.ResourcePackCallback> packCallbacks = new java.util.concurrent.ConcurrentHashMap<>(); // Paper - adventure resource pack callbacks
    private static final long KEEPALIVE_LIMIT = Long.getLong("paper.playerconnection.keepalive", 30) * 1000; // Paper - provide property to set keepalive limit
    protected static final ResourceLocation MINECRAFT_BRAND = ResourceLocation.withDefaultNamespace("brand"); // Paper - Brand support

    public ServerCommonPacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager, CommonListenerCookie commonlistenercookie, ServerPlayer player) { // CraftBukkit
        this.server = minecraftserver;
        this.connection = networkmanager;
        this.keepAliveTime = Util.getMillis();
        this.latency = commonlistenercookie.latency();
        this.transferred = commonlistenercookie.transferred();
        // CraftBukkit start - add fields and methods
        this.player = player;
        this.player.transferCookieConnection = this;
        this.cserver = minecraftserver.server;
    }
    protected final ServerPlayer player;
    protected final org.bukkit.craftbukkit.CraftServer cserver;
    public boolean processedDisconnect;

    public CraftPlayer getCraftPlayer() {
        return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
        // CraftBukkit end
    }

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = Util.getMillis();
            this.closed = true;
        }

    }

    @Override
    public void onDisconnect(DisconnectionDetails info) {
        if (this.isSingleplayerOwner()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }

    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        //PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel()); // CraftBukkit // Paper - handle ServerboundKeepAlivePacket async
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int i = (int) (Util.getMillis() - this.keepAliveTime);

            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            // Paper start - This needs to be handled on the main thread for plugins
            server.submit(() -> {
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
            });
            // Paper end - This needs to be handled on the main thread for plugins
        }

    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {}

    // CraftBukkit start
    private static final ResourceLocation CUSTOM_REGISTER = ResourceLocation.withDefaultNamespace("register");
    private static final ResourceLocation CUSTOM_UNREGISTER = ResourceLocation.withDefaultNamespace("unregister");

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // Paper start - Brand support
        if (packet.payload() instanceof net.minecraft.network.protocol.common.custom.BrandPayload brandPayload) {
            this.player.clientBrandName = brandPayload.brand();
        }
        // Paper end - Brand support
        if (!(packet.payload() instanceof DiscardedPayload)) {
            return;
        }
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        ResourceLocation identifier = packet.payload().type().id();
        ByteBuf payload = ((DiscardedPayload)packet.payload()).data();

        if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_REGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().addChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t register custom payload", ex);
                this.disconnect(Component.literal("Invalid payload REGISTER!"));
            }
        } else if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_UNREGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t unregister custom payload", ex);
                this.disconnect(Component.literal("Invalid payload UNREGISTER!"));
            }
        } else {
            try {
                byte[] data = new byte[payload.readableBytes()];
                payload.readBytes(data);
                // Paper start - Brand support; Retain this incase upstream decides to 'break' the new mechanism in favour of backwards compat...
                if (identifier.equals(MINECRAFT_BRAND)) {
                    try {
                        this.player.clientBrandName = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.copiedBuffer(data)).readUtf(256);
                    } catch (StringIndexOutOfBoundsException ex) {
                        this.player.clientBrandName = "illegal";
                    }
                }
                // Paper end - Brand support
                this.cserver.getMessenger().dispatchIncomingMessage(this.player.getBukkitEntity(), identifier.toString(), data);
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t dispatch custom payload", ex);
                this.disconnect(Component.literal("Invalid custom payload!"));
            }
        }

    }

    public final boolean isDisconnected() {
        return (!this.player.joining && !this.connection.isConnected()) || this.processedDisconnect; // Paper - Fix duplication bugs
    }
    // CraftBukkit end

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, (BlockableEventLoop) this.server);
        if (packet.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), packet.id());
            this.disconnect((Component) Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        }
        // Paper start - adventure pack callbacks
        // call the callbacks before the previously-existing event so the event has final say
        final net.kyori.adventure.resource.ResourcePackCallback callback;
        if (packet.action().isTerminal()) {
            callback = this.packCallbacks.remove(packet.id());
        } else {
            callback = this.packCallbacks.get(packet.id());
        }
        if (callback != null) {
            callback.packEventReceived(packet.id(), net.kyori.adventure.resource.ResourcePackStatus.valueOf(packet.action().name()), this.getCraftPlayer());
        }
        // Paper end
        // Paper start - store last pack status
        PlayerResourcePackStatusEvent.Status packStatus = PlayerResourcePackStatusEvent.Status.values()[packet.action().ordinal()];
        player.getBukkitEntity().resourcePackStatus = packStatus;
        this.cserver.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(this.getCraftPlayer(), packet.id(), packStatus)); // CraftBukkit
        // Paper end - store last pack status

    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket packet) {
        // CraftBukkit start
        PacketUtils.ensureRunningOnSameThread(packet, this, (BlockableEventLoop) this.server);
        if (this.player.getBukkitEntity().handleCookieResponse(packet)) {
            return;
        }
        // CraftBukkit end
        this.disconnect(ServerCommonPacketListenerImpl.DISCONNECT_UNEXPECTED_QUERY);
    }

    protected void keepConnectionAlive() {
        this.server.getProfiler().push("keepAlive");
        // Paper start - give clients a longer time to respond to pings as per pre 1.12.2 timings
        // This should effectively place the keepalive handling back to "as it was" before 1.12.2
        long currentTime = Util.getMillis();
        long elapsedTime = currentTime - this.keepAliveTime;

        if (!this.isSingleplayerOwner() && elapsedTime >= 15000L) { // Paper - use vanilla's 15000L between keep alive packets
            if (this.keepAlivePending && !this.processedDisconnect && elapsedTime >= KEEPALIVE_LIMIT) { // Paper - check keepalive limit, don't fire if already disconnected
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
            } else if (this.checkIfClosed(currentTime)) { // Paper
                this.keepAlivePending = true;
                this.keepAliveTime = currentTime;
                this.keepAliveChallenge = currentTime;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }
        // Paper end - give clients a longer time to respond to pings as per pre 1.12.2 timings

        this.server.getProfiler().pop();
    }

    private boolean checkIfClosed(long time) {
        if (this.closed) {
            if (time - this.closedListenerTime >= 15000L) {
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
            }

            return false;
        } else {
            return true;
        }
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, (PacketSendListener) null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        // CraftBukkit start
        if (packet == null || this.processedDisconnect) { // Spigot
            return;
        } else if (packet instanceof ClientboundSetDefaultSpawnPositionPacket) {
            ClientboundSetDefaultSpawnPositionPacket packet6 = (ClientboundSetDefaultSpawnPositionPacket) packet;
            this.player.compassTarget = CraftLocation.toBukkit(packet6.pos, this.getCraftPlayer().getWorld());
        }
        // CraftBukkit end
        if (packet.isTerminal()) {
            this.close();
        }

        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, callbacks, flag);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Packet being sent");

            crashreportsystemdetails.setDetail("Packet class", () -> {
                return packet.getClass().getCanonicalName();
            });
            throw new ReportedException(crashreport);
        }
    }

    // Paper start - adventure
    public void disconnect(final net.kyori.adventure.text.Component reason) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(reason));
    }
    // Paper end - adventure

    public void disconnect(Component reason) {
        this.disconnect(new DisconnectionDetails(reason));
    }

    public void disconnect(DisconnectionDetails disconnectionInfo) {
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        if (!this.cserver.isPrimaryThread()) {
            Waitable waitable = new Waitable() {
                @Override
                protected Object evaluate() {
                    ServerCommonPacketListenerImpl.this.disconnect(disconnectionInfo);
                    return null;
                }
            };

            this.server.processQueue.add(waitable);

            try {
                waitable.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        net.kyori.adventure.text.Component leaveMessage = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? this.player.getBukkitEntity().displayName() : net.kyori.adventure.text.Component.text(this.player.getScoreboardName())); // Paper - Adventure

        PlayerKickEvent event = new PlayerKickEvent(this.player.getBukkitEntity(), io.papermc.paper.adventure.PaperAdventure.asAdventure(disconnectionInfo.reason()), leaveMessage); // Paper - adventure

        if (this.cserver.getServer().isRunning()) {
            this.cserver.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        this.player.kickLeaveMessage = event.getLeaveMessage(); // CraftBukkit - SPIGOT-3034: Forward leave message to PlayerQuitEvent
        // Send the possibly modified leave message
        this.disconnect0(new DisconnectionDetails(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.reason()), disconnectionInfo.report(), disconnectionInfo.bugReportLink())); // Paper - Adventure
    }

    private void disconnect0(DisconnectionDetails disconnectiondetails) {
        // CraftBukkit end
        this.player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED; // Paper - Add API for quit reason
        this.connection.send(new ClientboundDisconnectPacket(disconnectiondetails.reason()), PacketSendListener.thenRun(() -> {
            this.connection.disconnect(disconnectiondetails);
        }));
        this.onDisconnect(disconnectiondetails); // CraftBukkit - fire quit instantly
        this.connection.setReadOnly();
        MinecraftServer minecraftserver = this.server;
        Connection networkmanager = this.connection;

        Objects.requireNonNull(this.connection);
        // CraftBukkit - Don't wait
        minecraftserver.scheduleOnMain(networkmanager::handleDisconnection); // Paper
    }

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation syncedOptions) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, syncedOptions, this.transferred);
    }
}
