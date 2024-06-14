package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public abstract class DistanceManager implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager, ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager { // Paper - rewrite chunk system // Paper - chunk tick iteration optimisation

    static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap();
    // Paper - rewrite chunk system
    // Paper - chunk tick iteration optimisation
    // Paper - rewrite chunk system
    private long ticketTickCounter;
    // Paper - rewrite chunk system

    protected DistanceManager(Executor workerExecutor, Executor mainThreadExecutor) {
        Objects.requireNonNull(mainThreadExecutor);
        ProcessorHandle<Runnable> mailbox = ProcessorHandle.of("player ticket throttler", mainThreadExecutor::execute);
        ChunkTaskPriorityQueueSorter chunktaskqueuesorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(mailbox), workerExecutor, 4);

        // Paper - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    public ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager getChunkHolderManager() {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration optimisation
    private final ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap<ServerPlayer> spawnChunkTracker = new ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap<>();

    @Override
    public final void moonrise$addPlayer(final ServerPlayer player, final SectionPos pos) {
        this.spawnChunkTracker.add(player, pos.x(), pos.z(), ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE);
    }

    @Override
    public final void moonrise$removePlayer(final ServerPlayer player, final SectionPos pos) {
        this.spawnChunkTracker.remove(player);
    }

    @Override
    public final void moonrise$updatePlayer(final ServerPlayer player,
                                            final SectionPos oldPos, final SectionPos newPos,
                                            final boolean oldIgnore, final boolean newIgnore) {
        if (newIgnore) {
            this.spawnChunkTracker.remove(player);
        } else {
            this.spawnChunkTracker.addOrUpdate(player, newPos.x(), newPos.z(), ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE);
        }
    }
    // Paper end - chunk tick iteration optimisation

    protected void purgeStaleTickets() {
        this.getChunkHolderManager().tick(); // Paper - rewrite chunk system

    }

    private static int getTicketLevelAt(SortedArraySet<Ticket<?>> tickets) {
        return !tickets.isEmpty() ? ((Ticket) tickets.first()).getTicketLevel() : ChunkLevel.MAX_LEVEL + 1;
    }

    protected abstract boolean isChunkToRemove(long pos);

    @Nullable
    protected abstract ChunkHolder getChunk(long pos);

    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k);

    public boolean runAllUpdates(ChunkMap chunkLoadingManager) {
        return this.getChunkHolderManager().processTicketUpdates(); // Paper - rewrite chunk system
    }

    boolean addTicket(long i, Ticket<?> ticket) { // CraftBukkit - void -> boolean
        return this.getChunkHolderManager().addTicketAtLevel((TicketType)ticket.getType(), i, ticket.getTicketLevel(), ticket.key); // Paper - rewrite chunk system
    }

    boolean removeTicket(long i, Ticket<?> ticket) { // CraftBukkit - void -> boolean
        return this.getChunkHolderManager().removeTicketAtLevel((TicketType)ticket.getType(), i, ticket.getTicketLevel(), ticket.key); // Paper - rewrite chunk system
    }

    public <T> void addTicket(TicketType<T> type, ChunkPos pos, int level, T argument) {
        this.addTicket(pos.toLong(), new Ticket<>(type, level, argument));
    }

    public <T> void removeTicket(TicketType<T> type, ChunkPos pos, int level, T argument) {
        Ticket<T> ticket = new Ticket<>(type, level, argument);

        this.removeTicket(pos.toLong(), ticket);
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int radius, T argument) {
        // CraftBukkit start
        this.addRegionTicketAtDistance(type, pos, radius, argument);
    }

    public <T> boolean addRegionTicketAtDistance(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        return this.getChunkHolderManager().addTicketAtLevel(tickettype, chunkcoordintpair, ChunkLevel.byStatus(FullChunkStatus.FULL) - i, t0); // Paper - rewrite chunk system
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int radius, T argument) {
        // CraftBukkit start
        this.removeRegionTicketAtDistance(type, pos, radius, argument);
    }

    public <T> boolean removeRegionTicketAtDistance(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        return this.getChunkHolderManager().removeTicketAtLevel(tickettype, chunkcoordintpair, ChunkLevel.byStatus(FullChunkStatus.FULL) - i, t0); // Paper - rewrite chunk system
    }

    private SortedArraySet<Ticket<?>> getTickets(long position) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected void updateChunkForced(ChunkPos pos, boolean forced) {
        // Paper start - rewrite chunk system
        if (forced) {
            this.getChunkHolderManager().addTicketAtLevel(TicketType.FORCED, pos, ChunkMap.FORCED_TICKET_LEVEL, pos);
        } else {
            this.getChunkHolderManager().removeTicketAtLevel(TicketType.FORCED, pos, ChunkMap.FORCED_TICKET_LEVEL, pos);
        }
        // Paper end - rewrite chunk system

    }

    public void addPlayer(SectionPos pos, ServerPlayer player) {
        ChunkPos chunkcoordintpair = pos.chunk();
        long i = chunkcoordintpair.toLong();

        ((ObjectSet) this.playersPerChunk.computeIfAbsent(i, (j) -> {
            return new ObjectOpenHashSet();
        })).add(player);
        // Paper - chunk tick iteration optimisation
        // Paper - rewrite chunk system
    }

    public void removePlayer(SectionPos pos, ServerPlayer player) {
        ChunkPos chunkcoordintpair = pos.chunk();
        long i = chunkcoordintpair.toLong();
        ObjectSet<ServerPlayer> objectset = (ObjectSet) this.playersPerChunk.get(i);
        if (objectset == null) return; // CraftBukkit - SPIGOT-6208

        if (objectset != null) objectset.remove(player); // Paper - some state corruption happens here, don't crash, clean up gracefully
        if (objectset == null || objectset.isEmpty()) { // Paper
            this.playersPerChunk.remove(i);
            // Paper - chunk tick iteration optimisation
            // Paper - rewrite chunk system
        }

    }

    private int getPlayerTicketLevel() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean inEntityTickingRange(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean inBlockTickingRange(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    protected String getTicketDebugString(long pos) {
        return this.getChunkHolderManager().getTicketDebugString(pos); // Paper - rewrite chunk system
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.moonrise$getChunkMap().setServerViewDistance(viewDistance); // Paper - rewrite chunk system
    }

    public void updateSimulationDistance(int simulationDistance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getPlayerChunkLoader().setTickDistance(simulationDistance); // Paper - rewrite chunk system

    }

    public int getNaturalSpawnChunkCount() {
        return this.spawnChunkTracker.getTotalPositions(); // Paper - chunk tick iteration optimisation
    }

    public boolean hasPlayersNearby(long chunkPos) {
        return this.spawnChunkTracker.hasObjectsNear(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkPos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkPos)); // Paper - chunk tick iteration optimisation
    }

    public String getDebugStatus() {
        return "No DistanceManager stats available"; // Paper - rewrite chunk system
    }

    private void dumpTickets(String path) {
        throw new UnsupportedOperationException();  // Paper - rewrite chunk system

    }

    @VisibleForTesting
    TickingTracker tickingTracker() {
        throw new UnsupportedOperationException();  // Paper - rewrite chunk system
    }

    public void removeTicketsOnClosing() {
        // Paper - rewrite chunk system

    }

    public boolean hasTickets() {
        throw new UnsupportedOperationException();  // Paper - rewrite chunk system
    }

    // CraftBukkit start
    public <T> void removeAllTicketsFor(TicketType<T> ticketType, int ticketLevel, T ticketIdentifier) {
        this.getChunkHolderManager().removeAllTicketsFor(ticketType, ticketLevel, ticketIdentifier); // Paper - rewrite chunk system
    }
    // CraftBukkit end

    /*  // Paper - rewrite chunk system
    private class ChunkTicketTracker extends ChunkTracker {

        private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;

        public ChunkTicketTracker() {
            super(DistanceManager.ChunkTicketTracker.MAX_LEVEL + 1, 16, 256);
        }

        @Override
        protected int getLevelFromSource(long id) {
            SortedArraySet<Ticket<?>> arraysetsorted = (SortedArraySet) DistanceManager.this.tickets.get(id);

            return arraysetsorted == null ? Integer.MAX_VALUE : (arraysetsorted.isEmpty() ? Integer.MAX_VALUE : ((Ticket) arraysetsorted.first()).getTicketLevel());
        }

        @Override
        protected int getLevel(long id) {
            if (!DistanceManager.this.isChunkToRemove(id)) {
                ChunkHolder playerchunk = DistanceManager.this.getChunk(id);

                if (playerchunk != null) {
                    return playerchunk.getTicketLevel();
                }
            }

            return DistanceManager.ChunkTicketTracker.MAX_LEVEL;
        }

        @Override
        protected void setLevel(long id, int level) {
            ChunkHolder playerchunk = DistanceManager.this.getChunk(id);
            int k = playerchunk == null ? DistanceManager.ChunkTicketTracker.MAX_LEVEL : playerchunk.getTicketLevel();

            if (k != level) {
                playerchunk = DistanceManager.this.updateChunkScheduling(id, level, playerchunk, k);
                if (playerchunk != null) {
                    DistanceManager.this.chunksToUpdateFutures.add(playerchunk);
                }

            }
        }

        public int runDistanceUpdates(int distance) {
            return this.runUpdates(distance);
        }
    }*/  // Paper - rewrite chunk system

    private class FixedPlayerDistanceChunkTracker extends ChunkTracker {

        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(final int i) {
            super(i + 2, 16, 256);
            this.maxDistance = i;
            this.chunks.defaultReturnValue((byte) (i + 2));
        }

        @Override
        protected int getLevel(long id) {
            return this.chunks.get(id);
        }

        @Override
        protected void setLevel(long id, int level) {
            byte b0;

            if (level > this.maxDistance) {
                b0 = this.chunks.remove(id);
            } else {
                b0 = this.chunks.put(id, (byte) level);
            }

            this.onLevelChange(id, b0, level);
        }

        protected void onLevelChange(long pos, int oldDistance, int distance) {}

        @Override
        protected int getLevelFromSource(long id) {
            return this.havePlayer(id) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long chunkPos) {
            ObjectSet<ServerPlayer> objectset = (ObjectSet) DistanceManager.this.playersPerChunk.get(chunkPos);

            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }

        private void dumpChunks(String path) {
            try {
                FileOutputStream fileoutputstream = new FileOutputStream(new File(path));

                try {
                    ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

                    while (objectiterator.hasNext()) {
                        it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry it_unimi_dsi_fastutil_longs_long2bytemap_entry = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry) objectiterator.next();
                        ChunkPos chunkcoordintpair = new ChunkPos(it_unimi_dsi_fastutil_longs_long2bytemap_entry.getLongKey());
                        String s1 = Byte.toString(it_unimi_dsi_fastutil_longs_long2bytemap_entry.getByteValue());

                        fileoutputstream.write((chunkcoordintpair.x + "\t" + chunkcoordintpair.z + "\t" + s1 + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable throwable) {
                    try {
                        fileoutputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                fileoutputstream.close();
            } catch (IOException ioexception) {
                DistanceManager.LOGGER.error("Failed to dump chunks to {}", path, ioexception);
            }

        }
    }

    /*  // Paper - rewrite chunk system
    private class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {

        private int viewDistance = 0;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(final int i) {
            super(i);
            this.queueLevels.defaultReturnValue(i + 2);
        }

        @Override
        protected void onLevelChange(long pos, int oldDistance, int distance) {
            this.toUpdate.add(pos);
        }

        public void updateViewDistance(int watchDistance) {
            ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

            while (objectiterator.hasNext()) {
                it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry it_unimi_dsi_fastutil_longs_long2bytemap_entry = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry) objectiterator.next();
                byte b0 = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getByteValue();
                long j = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getLongKey();

                this.onLevelChange(j, b0, this.haveTicketFor(b0), b0 <= watchDistance);
            }

            this.viewDistance = watchDistance;
        }

        private void onLevelChange(long pos, int distance, boolean oldWithinViewDistance, boolean withinViewDistance) {
            if (oldWithinViewDistance != withinViewDistance) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, DistanceManager.PLAYER_TICKET_LEVEL, new ChunkPos(pos));

                if (withinViewDistance) {
                    DistanceManager.this.ticketThrottlerInput.tell(ChunkTaskPriorityQueueSorter.message(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            if (this.haveTicketFor(this.getLevel(pos))) {
                                DistanceManager.this.addTicket(pos, ticket);
                                DistanceManager.this.ticketsToRelease.add(pos);
                            } else {
                                DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                                }, pos, false));
                            }

                        });
                    }, pos, () -> {
                        return distance;
                    }));
                } else {
                    DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            DistanceManager.this.removeTicket(pos, ticket);
                        });
                    }, pos, true));
                }
            }

        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.queueLevels.get(i);
                    int k = this.getLevel(i);

                    if (j != k) {
                        DistanceManager.this.ticketThrottler.onLevelChange(new ChunkPos(i), () -> {
                            return this.queueLevels.get(i);
                        }, k, (l) -> {
                            if (l >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(i);
                            } else {
                                this.queueLevels.put(i, l);
                            }

                        });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }

        }

        private boolean haveTicketFor(int distance) {
            return distance <= this.viewDistance;
        }
    }*/  // Paper - rewrite chunk system
}
