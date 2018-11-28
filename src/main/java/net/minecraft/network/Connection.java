package net.minecraft.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {

    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = (Marker) Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), (marker) -> {
        marker.add(Connection.ROOT_MARKER);
    });
    public static final Marker PACKET_RECEIVED_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final Marker PACKET_SENT_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_SENT"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final Supplier<NioEventLoopGroup> NETWORK_WORKER_GROUP = Suppliers.memoize(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<EpollEventLoopGroup> NETWORK_EPOLL_WORKER_GROUP = Suppliers.memoize(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<DefaultEventLoopGroup> LOCAL_WORKER_GROUP = Suppliers.memoize(() -> {
        return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    private static final ProtocolInfo<ServerHandshakePacketListener> INITIAL_PROTOCOL = HandshakeProtocols.SERVERBOUND;
    private final PacketFlow receiving;
    private volatile boolean sendLoginDisconnect = true;
    private final Queue<Consumer<Connection>> pendingActions = Queues.newConcurrentLinkedQueue();
    public Channel channel;
    public SocketAddress address;
    // Spigot Start
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public boolean preparing = true;
    // Spigot End
    @Nullable
    private volatile PacketListener disconnectListener;
    @Nullable
    private volatile PacketListener packetListener;
    @Nullable
    private DisconnectionDetails disconnectionDetails;
    private boolean encrypted;
    private boolean disconnectionHandled;
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    @Nullable
    private volatile DisconnectionDetails delayedDisconnect;
    @Nullable
    BandwidthDebugMonitor bandwidthDebugMonitor;
    public String hostname = ""; // CraftBukkit - add field
    // Paper start - NetworkClient implementation
    public int protocolVersion;
    public java.net.InetSocketAddress virtualHost;
    private static boolean enableExplicitFlush = Boolean.getBoolean("paper.explicit-flush"); // Paper - Disable explicit network manager flushing
    // Paper end

    // Paper start - add utility methods
    public final net.minecraft.server.level.ServerPlayer getPlayer() {
        if (this.packetListener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl impl) {
            return impl.player;
        } else if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = impl.getCraftPlayer();
            return player == null ? null : player.getHandle();
        }
        return null;
    }
    // Paper end - add utility methods
    // Paper start - packet limiter
    protected final Object PACKET_LIMIT_LOCK = new Object();
    protected final @Nullable io.papermc.paper.util.IntervalledCounter allPacketCounts = io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.isEnabled() ? new io.papermc.paper.util.IntervalledCounter(
        (long)(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.interval() * 1.0e9)
    ) : null;
    protected final java.util.Map<Class<? extends net.minecraft.network.protocol.Packet<?>>, io.papermc.paper.util.IntervalledCounter> packetSpecificLimits = new java.util.HashMap<>();

    private boolean stopReadingPackets;
    private void killForPacketSpam() {
        this.sendPacket(new ClientboundDisconnectPacket(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.kickMessage)), PacketSendListener.thenRun(() -> {
            this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.kickMessage));
        }), true);
        this.setReadOnly();
        this.stopReadingPackets = true;
    }
    // Paper end - packet limiter

    public Connection(PacketFlow side) {
        this.receiving = side;
    }

    public void channelActive(ChannelHandlerContext channelhandlercontext) throws Exception {
        super.channelActive(channelhandlercontext);
        this.channel = channelhandlercontext.channel();
        this.address = this.channel.remoteAddress();
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }

    }

    public void channelInactive(ChannelHandlerContext channelhandlercontext) {
        this.disconnect((Component) Component.translatable("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext channelhandlercontext, Throwable throwable) {
        // Paper start - Handle large packets disconnecting client
        if (throwable instanceof io.netty.handler.codec.EncoderException && throwable.getCause() instanceof PacketEncoder.PacketTooLargeException packetTooLargeException) {
            final Packet<?> packet = packetTooLargeException.getPacket();
            if (packet.packetTooLarge(this)) {
                ProtocolSwapHandler.handleOutboundTerminalPacket(channelhandlercontext, packet);
                return;
            } else if (packet.isSkippable()) {
                Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
                ProtocolSwapHandler.handleOutboundTerminalPacket(channelhandlercontext, packet);
                return;
            } else {
                throwable = throwable.getCause();
            }
        }
        // Paper end - Handle large packets disconnecting client
        if (throwable instanceof SkipPacketException) {
            Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
        } else {
            boolean flag = !this.handlingFault;

            this.handlingFault = true;
            if (this.channel.isOpen()) {
                net.minecraft.server.level.ServerPlayer player = this.getPlayer(); // Paper - Add API for quit reason
                if (throwable instanceof TimeoutException) {
                    Connection.LOGGER.debug("Timeout", throwable);
                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.TIMED_OUT; // Paper - Add API for quit reason
                    this.disconnect((Component) Component.translatable("disconnect.timeout"));
                } else {
                    MutableComponent ichatmutablecomponent = Component.translatable("disconnect.genericReason", "Internal Exception: " + String.valueOf(throwable));
                    PacketListener packetlistener = this.packetListener;
                    DisconnectionDetails disconnectiondetails;

                    if (packetlistener != null) {
                        disconnectiondetails = packetlistener.createDisconnectionInfo(ichatmutablecomponent, throwable);
                    } else {
                        disconnectiondetails = new DisconnectionDetails(ichatmutablecomponent);
                    }

                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.ERRONEOUS_STATE; // Paper - Add API for quit reason
                    if (flag) {
                        Connection.LOGGER.debug("Failed to sent packet", throwable);
                        if (this.getSending() == PacketFlow.CLIENTBOUND) {
                            Packet<?> packet = this.sendLoginDisconnect ? new ClientboundLoginDisconnectPacket(ichatmutablecomponent) : new ClientboundDisconnectPacket(ichatmutablecomponent);

                            this.send((Packet) packet, PacketSendListener.thenRun(() -> {
                                this.disconnect(disconnectiondetails);
                            }));
                        } else {
                            this.disconnect(disconnectiondetails);
                        }

                        this.setReadOnly();
                    } else {
                        Connection.LOGGER.debug("Double fault", throwable);
                        this.disconnect(disconnectiondetails);
                    }
                }

            }
        }
        if (net.minecraft.server.MinecraftServer.getServer().isDebugging()) io.papermc.paper.util.TraceUtil.printStackTrace(throwable); // Spigot // Paper
    }

    protected void channelRead0(ChannelHandlerContext channelhandlercontext, Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetlistener = this.packetListener;

            if (packetlistener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                // Paper start - packet limiter
                if (this.stopReadingPackets) {
                    return;
                }
                if (this.allPacketCounts != null ||
                    io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.overrides.containsKey(packet.getClass())) {
                    long time = System.nanoTime();
                    synchronized (PACKET_LIMIT_LOCK) {
                        if (this.allPacketCounts != null) {
                            this.allPacketCounts.updateAndAdd(1, time);
                            if (this.allPacketCounts.getRate() >= io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.maxPacketRate()) {
                                this.killForPacketSpam();
                                return;
                            }
                        }

                        for (Class<?> check = packet.getClass(); check != Object.class; check = check.getSuperclass()) {
                            io.papermc.paper.configuration.GlobalConfiguration.PacketLimiter.PacketLimit packetSpecificLimit =
                                io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.overrides.get(check);
                            if (packetSpecificLimit == null || !packetSpecificLimit.isEnabled()) {
                                continue;
                            }
                            io.papermc.paper.util.IntervalledCounter counter = this.packetSpecificLimits.computeIfAbsent((Class)check, (clazz) -> {
                                return new io.papermc.paper.util.IntervalledCounter((long)(packetSpecificLimit.interval() * 1.0e9));
                            });
                            counter.updateAndAdd(1, time);
                            if (counter.getRate() >= packetSpecificLimit.maxPacketRate()) {
                                switch (packetSpecificLimit.action()) {
                                    case DROP:
                                        return;
                                    case KICK:
                                        String deobfedPacketName = io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(check.getName());

                                        String playerName;
                                        if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
                                            playerName = impl.getOwner().getName();
                                        } else {
                                            playerName = this.getLoggableAddress(net.minecraft.server.MinecraftServer.getServer().logIPs());
                                        }

                                        Connection.LOGGER.warn("{} kicked for packet spamming: {}", playerName, deobfedPacketName.substring(deobfedPacketName.lastIndexOf(".") + 1));
                                        this.killForPacketSpam();
                                        return;
                                }
                            }
                        }
                    }
                }
                // Paper end - packet limiter
                if (packetlistener.shouldHandleMessage(packet)) {
                    try {
                        Connection.genericsFtw(packet, packetlistener);
                    } catch (RunningOnDifferentThreadException cancelledpackethandleexception) {
                        ;
                    } catch (io.papermc.paper.util.ServerStopRejectedExecutionException ignored) { // Paper - do not prematurely disconnect players on stop
                    } catch (RejectedExecutionException rejectedexecutionexception) {
                        this.disconnect((Component) Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException classcastexception) {
                        Connection.LOGGER.error("Received {} that couldn't be processed", packet.getClass(), classcastexception);
                        this.disconnect((Component) Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    ++this.receivedPackets;
                }

            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        packet.handle((T) listener); // CraftBukkit - decompile error
    }

    private void validateListener(ProtocolInfo<?> state, PacketListener listener) {
        Validate.notNull(listener, "packetListener", new Object[0]);
        PacketFlow enumprotocoldirection = listener.flow();
        String s;

        if (enumprotocoldirection != this.receiving) {
            s = String.valueOf(this.receiving);
            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + s + ", but listener is " + String.valueOf(enumprotocoldirection));
        } else {
            ConnectionProtocol enumprotocol = listener.protocol();

            if (state.id() != enumprotocol) {
                s = String.valueOf(enumprotocol);
                throw new IllegalStateException("Listener protocol (" + s + ") does not match requested one " + String.valueOf(state));
            }
        }
    }

    private static void syncAfterConfigurationChange(ChannelFuture future) {
        try {
            future.syncUninterruptibly();
        } catch (Exception exception) {
            if (exception instanceof ClosedChannelException) {
                Connection.LOGGER.info("Connection closed during protocol change");
            } else {
                throw exception;
            }
        }
    }

    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> state, T packetListener) {
        this.validateListener(state, packetListener);
        if (state.flow() != this.getReceiving()) {
            throw new IllegalStateException("Invalid inbound protocol: " + String.valueOf(state.id()));
        } else {
            this.packetListener = packetListener;
            this.disconnectListener = null;
            UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler_b = UnconfiguredPipelineHandler.setupInboundProtocol(state);
            BundlerInfo bundlerinfo = state.bundlerInfo();

            if (bundlerinfo != null) {
                PacketBundlePacker packetbundlepacker = new PacketBundlePacker(bundlerinfo);

                unconfiguredpipelinehandler_b = unconfiguredpipelinehandler_b.andThen((channelhandlercontext) -> {
                    channelhandlercontext.pipeline().addAfter("decoder", "bundler", packetbundlepacker);
                });
            }

            Connection.syncAfterConfigurationChange(this.channel.writeAndFlush(unconfiguredpipelinehandler_b));
        }
    }

    public void setupOutboundProtocol(ProtocolInfo<?> newState) {
        if (newState.flow() != this.getSending()) {
            throw new IllegalStateException("Invalid outbound protocol: " + String.valueOf(newState.id()));
        } else {
            UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler_d = UnconfiguredPipelineHandler.setupOutboundProtocol(newState);
            BundlerInfo bundlerinfo = newState.bundlerInfo();

            if (bundlerinfo != null) {
                PacketBundleUnpacker packetbundleunpacker = new PacketBundleUnpacker(bundlerinfo);

                unconfiguredpipelinehandler_d = unconfiguredpipelinehandler_d.andThen((channelhandlercontext) -> {
                    channelhandlercontext.pipeline().addAfter("encoder", "unbundler", packetbundleunpacker);
                });
            }

            boolean flag = newState.id() == ConnectionProtocol.LOGIN;

            Connection.syncAfterConfigurationChange(this.channel.writeAndFlush(unconfiguredpipelinehandler_d.andThen((channelhandlercontext) -> {
                this.sendLoginDisconnect = flag;
            })));
        }
    }

    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND && packetListener.flow() == PacketFlow.SERVERBOUND && packetListener.protocol() == Connection.INITIAL_PROTOCOL.id()) {
            this.packetListener = packetListener;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String address, int port, ClientStatusPacketListener listener) {
        this.initiateServerboundConnection(address, port, StatusProtocols.SERVERBOUND, StatusProtocols.CLIENTBOUND, listener, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String address, int port, ClientLoginPacketListener listener) {
        this.initiateServerboundConnection(address, port, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND, listener, ClientIntent.LOGIN);
    }

    public <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundPlayConnection(String address, int port, ProtocolInfo<S> outboundState, ProtocolInfo<C> inboundState, C prePlayStateListener, boolean transfer) {
        this.initiateServerboundConnection(address, port, outboundState, inboundState, prePlayStateListener, transfer ? ClientIntent.TRANSFER : ClientIntent.LOGIN);
    }

    private <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundConnection(String address, int port, ProtocolInfo<S> outboundState, ProtocolInfo<C> inboundState, C prePlayStateListener, ClientIntent intent) {
        if (outboundState.id() != inboundState.id()) {
            throw new IllegalStateException("Mismatched initial protocols");
        } else {
            this.disconnectListener = prePlayStateListener;
            this.runOnceConnected((networkmanager) -> {
                this.setupInboundProtocol(inboundState, prePlayStateListener);
                networkmanager.sendPacket(new ClientIntentionPacket(SharedConstants.getCurrentVersion().getProtocolVersion(), address, port, intent), (PacketSendListener) null, true);
                this.setupOutboundProtocol(outboundState);
            });
        }
    }

    public void send(Packet<?> packet) {
        this.send(packet, (PacketSendListener) null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        this.send(packet, callbacks, true);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        if (this.isConnected()) {
            this.flushQueue();
            this.sendPacket(packet, callbacks, flush);
        } else {
            this.pendingActions.add((networkmanager) -> {
                networkmanager.sendPacket(packet, callbacks, flush);
            });
        }

    }

    public void runOnceConnected(Consumer<Connection> task) {
        if (this.isConnected()) {
            this.flushQueue();
            task.accept(this);
        } else {
            this.pendingActions.add(task);
        }

    }

    private void sendPacket(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        ++this.sentPackets;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(packet, callbacks, flush);
        } else {
            this.channel.eventLoop().execute(() -> {
                this.doSendPacket(packet, callbacks, flush);
            });
        }

    }

    private void doSendPacket(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        ChannelFuture channelfuture = flush ? this.channel.writeAndFlush(packet) : this.channel.write(packet);

        if (callbacks != null) {
            channelfuture.addListener((future) -> {
                if (future.isSuccess()) {
                    callbacks.onSuccess();
                } else {
                    Packet<?> packet1 = callbacks.onFailure();

                    if (packet1 != null) {
                        ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet1);

                        channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    }
                }

            });
        }

        channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public void flushChannel() {
        if (this.isConnected()) {
            this.flush();
        } else {
            this.pendingActions.add(Connection::flush);
        }

    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> {
                this.channel.flush();
            });
        }

    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            Queue queue = this.pendingActions;

            synchronized (this.pendingActions) {
                Consumer consumer;

                while ((consumer = (Consumer) this.pendingActions.poll()) != null) {
                    consumer.accept(this);
                }

            }
        }
    }

    private static final int MAX_PER_TICK = io.papermc.paper.configuration.GlobalConfiguration.get().misc.maxJoinsPerTick; // Paper - Buffer joins to world
    private static int joinAttemptsThisTick; // Paper - Buffer joins to world
    private static int currTick; // Paper - Buffer joins to world
    public void tick() {
        this.flushQueue();
        // Paper start - Buffer joins to world
        if (Connection.currTick != net.minecraft.server.MinecraftServer.currentTick) {
            Connection.currTick = net.minecraft.server.MinecraftServer.currentTick;
            Connection.joinAttemptsThisTick = 0;
        }
        // Paper end - Buffer joins to world
        PacketListener packetlistener = this.packetListener;

        if (packetlistener instanceof TickablePacketListener tickablepacketlistener) {
            // Paper start - Buffer joins to world
            if (!(this.packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginPacketListener)
                || loginPacketListener.state != net.minecraft.server.network.ServerLoginPacketListenerImpl.State.VERIFYING
                || Connection.joinAttemptsThisTick++ < MAX_PER_TICK) {
            tickablepacketlistener.tick();
            } // Paper end - Buffer joins to world
        }

        if (!this.isConnected() && !this.disconnectionHandled) {
            this.handleDisconnection();
        }

        if (this.channel != null) {
            if (enableExplicitFlush) this.channel.eventLoop().execute(() -> this.channel.flush()); // Paper - Disable explicit network manager flushing; we don't need to explicit flush here, but allow opt in incase issues are found to a better version
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }

    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float) this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float) this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean logIps) {
        return this.address == null ? "local" : (logIps ? this.address.toString() : "IP hidden");
    }

    public void disconnect(Component disconnectReason) {
        this.disconnect(new DisconnectionDetails(disconnectReason));
    }

    public void disconnect(DisconnectionDetails disconnectionInfo) {
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.channel == null) {
            this.delayedDisconnect = disconnectionInfo;
        }

        if (this.isConnected()) {
            this.channel.close(); // We can't wait as this may be called from an event loop.
            this.disconnectionDetails = disconnectionInfo;
        }

    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress address, boolean useEpoll, @Nullable LocalSampleLogger packetSizeLog) {
        Connection networkmanager = new Connection(PacketFlow.CLIENTBOUND);

        if (packetSizeLog != null) {
            networkmanager.setBandwidthLogger(packetSizeLog);
        }

        ChannelFuture channelfuture = Connection.connect(address, useEpoll, networkmanager);

        channelfuture.syncUninterruptibly();
        return networkmanager;
    }

    public static ChannelFuture connect(InetSocketAddress address, boolean useEpoll, final Connection connection) {
        Class oclass;
        EventLoopGroup eventloopgroup;

        if (Epoll.isAvailable() && useEpoll) {
            oclass = EpollSocketChannel.class;
            eventloopgroup = (EventLoopGroup) Connection.NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            oclass = NioSocketChannel.class;
            eventloopgroup = (EventLoopGroup) Connection.NETWORK_WORKER_GROUP.get();
        }

        return ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group(eventloopgroup)).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                    ;
                }

                ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));

                Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, false, connection.bandwidthDebugMonitor);
                connection.configurePacketHandler(channelpipeline);
            }
        })).channel(oclass)).connect(address.getAddress(), address.getPort());
    }

    private static String outboundHandlerName(boolean sendingSide) {
        return sendingSide ? "encoder" : "outbound_config";
    }

    private static String inboundHandlerName(boolean receivingSide) {
        return receivingSide ? "decoder" : "inbound_config";
    }

    public void configurePacketHandler(ChannelPipeline pipeline) {
        pipeline.addLast("hackfix", new ChannelOutboundHandlerAdapter() { // CraftBukkit - decompile error
            public void write(ChannelHandlerContext channelhandlercontext, Object object, ChannelPromise channelpromise) throws Exception {
                super.write(channelhandlercontext, object, channelpromise);
            }
        }).addLast("packet_handler", this);
    }

    public static void configureSerialization(ChannelPipeline pipeline, PacketFlow side, boolean local, @Nullable BandwidthDebugMonitor packetSizeLogger) {
        PacketFlow enumprotocoldirection1 = side.getOpposite();
        boolean flag1 = side == PacketFlow.SERVERBOUND;
        boolean flag2 = enumprotocoldirection1 == PacketFlow.SERVERBOUND;

        pipeline.addLast("splitter", Connection.createFrameDecoder(packetSizeLogger, local)).addLast(new ChannelHandler[]{new FlowControlHandler()}).addLast(Connection.inboundHandlerName(flag1), (ChannelHandler) (flag1 ? new PacketDecoder<>(Connection.INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Inbound())).addLast("prepender", Connection.createFrameEncoder(local)).addLast(Connection.outboundHandlerName(flag2), (ChannelHandler) (flag2 ? new PacketEncoder<>(Connection.INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Outbound()));
    }

    private static ChannelOutboundHandler createFrameEncoder(boolean local) {
        return (ChannelOutboundHandler) (local ? new NoOpFrameEncoder() : new Varint21LengthFieldPrepender());
    }

    private static ChannelInboundHandler createFrameDecoder(@Nullable BandwidthDebugMonitor packetSizeLogger, boolean local) {
        return (ChannelInboundHandler) (!local ? new Varint21FrameDecoder(packetSizeLogger) : (packetSizeLogger != null ? new MonitorFrameDecoder(packetSizeLogger) : new NoOpFrameDecoder()));
    }

    public static void configureInMemoryPipeline(ChannelPipeline pipeline, PacketFlow side) {
        Connection.configureSerialization(pipeline, side, true, (BandwidthDebugMonitor) null);
    }

    public static Connection connectToLocalServer(SocketAddress address) {
        final Connection networkmanager = new Connection(PacketFlow.CLIENTBOUND);

        ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group((EventLoopGroup) Connection.LOCAL_WORKER_GROUP.get())).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                ChannelPipeline channelpipeline = channel.pipeline();

                Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.CLIENTBOUND);
                networkmanager.configurePacketHandler(channelpipeline);
            }
        })).channel(LocalChannel.class)).connect(address).syncUninterruptibly();
        return networkmanager;
    }

    public void setEncryptionKey(Cipher decryptionCipher, Cipher encryptionCipher) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryptionCipher));
        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryptionCipher));
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    @Nullable
    public PacketListener getPacketListener() {
        return this.packetListener;
    }

    @Nullable
    public DisconnectionDetails getDisconnectionDetails() {
        return this.disconnectionDetails;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }

    }

    public void setupCompression(int compressionThreshold, boolean rejectsBadPackets) {
        if (compressionThreshold >= 0) {
            ChannelHandler channelhandler = this.channel.pipeline().get("decompress");

            if (channelhandler instanceof CompressionDecoder) {
                CompressionDecoder packetdecompressor = (CompressionDecoder) channelhandler;

                packetdecompressor.setThreshold(compressionThreshold, rejectsBadPackets);
            } else {
                this.channel.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(compressionThreshold, rejectsBadPackets));
            }

            channelhandler = this.channel.pipeline().get("compress");
            if (channelhandler instanceof CompressionEncoder) {
                CompressionEncoder packetcompressor = (CompressionEncoder) channelhandler;

                packetcompressor.setThreshold(compressionThreshold);
            } else {
                this.channel.pipeline().addAfter("prepender", "compress", new CompressionEncoder(compressionThreshold));
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_THRESHOLD_SET); // Paper - Add Channel initialization listeners
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_DISABLED); // Paper - Add Channel initialization listeners
        }

    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled) {
                Connection.LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnectionHandled = true;
                PacketListener packetlistener = this.getPacketListener();
                PacketListener packetlistener1 = packetlistener != null ? packetlistener : this.disconnectListener;

                if (packetlistener1 != null) {
                    DisconnectionDetails disconnectiondetails = (DisconnectionDetails) Objects.requireNonNullElseGet(this.getDisconnectionDetails(), () -> {
                        return new DisconnectionDetails(Component.translatable("multiplayer.disconnect.generic"));
                    });

                    packetlistener1.onDisconnect(disconnectiondetails);
                }
                this.pendingActions.clear(); // Free up packet queue.
                // Paper start - Add PlayerConnectionCloseEvent
                final PacketListener packetListener = this.getPacketListener();
                if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                    /* Player was logged in, either game listener or configuration listener */
                    final com.mojang.authlib.GameProfile profile = commonPacketListener.getOwner();
                    new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.getId(),
                        profile.getName(), ((InetSocketAddress) this.address).getAddress(), false).callEvent();
                } else if (packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginListener) {
                    /* Player is login stage */
                    switch (loginListener.state) {
                        case VERIFYING:
                        case WAITING_FOR_DUPE_DISCONNECT:
                        case PROTOCOL_SWITCHING:
                        case ACCEPTED:
                            final com.mojang.authlib.GameProfile profile = loginListener.authenticatedProfile; /* Should be non-null at this stage */
                            new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.getId(), profile.getName(),
                                ((InetSocketAddress) this.address).getAddress(), false).callEvent();
                    }
                }
                // Paper end - Add PlayerConnectionCloseEvent

            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(LocalSampleLogger log) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(log);
    }
}
