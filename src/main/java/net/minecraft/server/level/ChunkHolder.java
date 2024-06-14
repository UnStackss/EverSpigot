package net.minecraft.server.level;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
// CraftBukkit end

public class ChunkHolder extends GenerationChunkHolder implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder { // Paper - rewrite chunk system

    public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
    private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    private final LevelHeightAccessor levelHeightAccessor;
    // Paper - rewrite chunk system
    private boolean hasChangedSections;
    private final ShortSet[] changedBlocksPerSection;
    private final BitSet blockChangedLightSectionFilter;
    private final BitSet skyChangedLightSectionFilter;
    private final LevelLightEngine lightEngine;
    // Paper - rewrite chunk system
    public final ChunkHolder.PlayerProvider playerProvider;
    // Paper - rewrite chunk system

    // Paper start - rewrite chunk system
    private ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder;

    private static final ServerPlayer[] EMPTY_PLAYER_ARRAY = new ServerPlayer[0];
    private final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> playersSentChunkTo = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_PLAYER_ARRAY);

    private ChunkMap getChunkMap() {
        return (ChunkMap)this.playerProvider;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder moonrise$getRealChunkHolder() {
        return this.newChunkHolder;
    }

    @Override
    public final void moonrise$setRealChunkHolder(final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder) {
        this.newChunkHolder = newChunkHolder;
    }

    @Override
    public final void moonrise$addReceivedChunk(final ServerPlayer player) {
        if (!this.playersSentChunkTo.add(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.getChunkMap().level) + "' to player " + player);
        }
    }

    @Override
    public final void moonrise$removeReceivedChunk(final ServerPlayer player) {
        if (!this.playersSentChunkTo.remove(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.getChunkMap().level) + "' to player " + player);
        }
    }

    @Override
    public final boolean moonrise$hasChunkBeenSent() {
        return this.playersSentChunkTo.size() != 0;
    }

    @Override
    public final boolean moonrise$hasChunkBeenSent(final ServerPlayer to) {
        return this.playersSentChunkTo.contains(to);
    }

    @Override
    public final List<ServerPlayer> moonrise$getPlayers(final boolean onlyOnWatchDistanceEdge) {
        final List<ServerPlayer> ret = new java.util.ArrayList<>();
        final ServerPlayer[] raw = this.playersSentChunkTo.getRawDataUnchecked();
        for (int i = 0, len = this.playersSentChunkTo.size(); i < len; ++i) {
            final ServerPlayer player = raw[i];
            if (onlyOnWatchDistanceEdge && !((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.getChunkMap().level).moonrise$getPlayerChunkLoader().isChunkSent(player, this.pos.x, this.pos.z, onlyOnWatchDistanceEdge)) {
                continue;
            }
            ret.add(player);
        }

        return ret;
    }

    @Override
    public final LevelChunk moonrise$getFullChunk() {
        if (this.newChunkHolder.isFullChunkReady()) {
            if (this.newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
                return levelChunk;
            } // else: race condition: chunk unload
        }
        return null;
    }

    private boolean isRadiusLoaded(final int radius) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.getChunkMap().level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager;
        final ChunkPos pos = this.pos;
        final int chunkX = pos.x;
        final int chunkZ = pos.z;
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                if ((dx | dz) == 0) {
                    continue;
                }

                final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = manager.getChunkHolder(dx + chunkX, dz + chunkZ);

                if (holder == null || !holder.isFullChunkReady()) {
                    return false;
                }
            }
        }

        return true;
    }
    // Paper end - rewrite chunk system

    public ChunkHolder(ChunkPos pos, int level, LevelHeightAccessor world, LevelLightEngine lightingProvider, ChunkHolder.LevelChangeListener levelUpdateListener, ChunkHolder.PlayerProvider playersWatchingChunkProvider) {
        super(pos);
        // Paper - rewrite chunk system
        this.blockChangedLightSectionFilter = new BitSet();
        this.skyChangedLightSectionFilter = new BitSet();
        // Paper - rewrite chunk system
        this.levelHeightAccessor = world;
        this.lightEngine = lightingProvider;
        // Paper - rewrite chunk system
        this.playerProvider = playersWatchingChunkProvider;
        // Paper - rewrite chunk system
        this.setTicketLevel(level);
        this.changedBlocksPerSection = new ShortSet[world.getSectionsCount()];
    }

    // CraftBukkit start
    public LevelChunk getFullChunkNow() {
        // Note: We use the oldTicketLevel for isLoaded checks.
        if (!this.newChunkHolder.isFullChunkReady()) return null; // Paper - rewrite chunk system
        return this.getFullChunkNowUnchecked();
    }

    public LevelChunk getFullChunkNowUnchecked() {
        return (LevelChunk) this.getChunkIfPresentUnchecked(ChunkStatus.FULL);
    }
    // CraftBukkit end

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    public final LevelChunk getTickingChunk() { // Paper - final for inline
        // Paper start - rewrite chunk system
        if (this.newChunkHolder.isTickingReady()) {
            if (this.newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
                return levelChunk;
            } // else: race condition: chunk unload
        }
        return null;
        // Paper end - rewrite chunk system
    }

    @Nullable
    public LevelChunk getChunkToSend() {
        // Paper start - rewrite chunk system
        final LevelChunk ret = this.moonrise$getFullChunk();
        if (ret != null && this.isRadiusLoaded(1)) {
            return ret;
        }
        return null;
        // Paper end - rewrite chunk system
    }

    public CompletableFuture<?> getSendSyncFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void addSendDependency(CompletableFuture<?> postProcessingFuture) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system

    }

    public CompletableFuture<?> getSaveSyncFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean isReadyForSaving() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void addSaveDependency(CompletableFuture<?> savingFuture) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system

    }

    public void blockChanged(BlockPos pos) {
        LevelChunk chunk = this.playersSentChunkTo.size() == 0 ? null : this.getChunkToSend(); // Paper - rewrite chunk system

        if (chunk != null) {
            int i = this.levelHeightAccessor.getSectionIndex(pos.getY());

            if (i < 0 || i >= this.changedBlocksPerSection.length) return; // CraftBukkit - SPIGOT-6086, SPIGOT-6296
            if (this.changedBlocksPerSection[i] == null) {
                this.hasChangedSections = true;
                this.changedBlocksPerSection[i] = new ShortOpenHashSet();
            }

            this.changedBlocksPerSection[i].add(SectionPos.sectionRelativePos(pos));
        }
    }

    public void sectionLightChanged(LightLayer lightType, int y) {
        ChunkAccess ichunkaccess = this.getChunkIfPresent(ChunkStatus.INITIALIZE_LIGHT);

        if (ichunkaccess != null) {
            ichunkaccess.setUnsaved(true);
            LevelChunk chunk = this.getChunkToSend(); // Paper - rewrite chunk system

            if (chunk != null) {
                int j = this.lightEngine.getMinLightSection();
                int k = this.lightEngine.getMaxLightSection();

                if (y >= j && y <= k) {
                    int l = y - j;

                    if (lightType == LightLayer.SKY) {
                        this.skyChangedLightSectionFilter.set(l);
                    } else {
                        this.blockChangedLightSectionFilter.set(l);
                    }

                }
            }
        }
    }

    public void broadcastChanges(LevelChunk chunk) {
        if (this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
            Level world = chunk.getLevel();
            List list;

            if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
                list = this.moonrise$getPlayers(true); // Paper - rewrite chunk system
                if (!list.isEmpty()) {
                    ClientboundLightUpdatePacket packetplayoutlightupdate = new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter);

                    this.broadcast(list, packetplayoutlightupdate);
                }

                this.skyChangedLightSectionFilter.clear();
                this.blockChangedLightSectionFilter.clear();
            }

            if (this.hasChangedSections) {
                list = this.moonrise$getPlayers(false); // Paper - rewrite chunk system

                for (int i = 0; i < this.changedBlocksPerSection.length; ++i) {
                    ShortSet shortset = this.changedBlocksPerSection[i];

                    if (shortset != null) {
                        this.changedBlocksPerSection[i] = null;
                        if (!list.isEmpty()) {
                            int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                            SectionPos sectionposition = SectionPos.of(chunk.getPos(), j);

                            if (shortset.size() == 1) {
                                BlockPos blockposition = sectionposition.relativeToBlockPos(shortset.iterator().nextShort());
                                BlockState iblockdata = world.getBlockState(blockposition);

                                this.broadcast(list, new ClientboundBlockUpdatePacket(blockposition, iblockdata));
                                this.broadcastBlockEntityIfNeeded(list, world, blockposition, iblockdata);
                            } else {
                                LevelChunkSection chunksection = chunk.getSection(i);
                                ClientboundSectionBlocksUpdatePacket packetplayoutmultiblockchange = new ClientboundSectionBlocksUpdatePacket(sectionposition, shortset, chunksection);

                                this.broadcast(list, packetplayoutmultiblockchange);
                                // CraftBukkit start
                                List finalList = list;
                                packetplayoutmultiblockchange.runUpdates((blockposition1, iblockdata1) -> {
                                    this.broadcastBlockEntityIfNeeded(finalList, world, blockposition1, iblockdata1);
                                    // CraftBukkit end
                                });
                            }
                        }
                    }
                }

                this.hasChangedSections = false;
            }
        }
    }

    private void broadcastBlockEntityIfNeeded(List<ServerPlayer> players, Level world, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) {
            this.broadcastBlockEntity(players, world, pos);
        }

    }

    private void broadcastBlockEntity(List<ServerPlayer> players, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity != null) {
            Packet<?> packet = tileentity.getUpdatePacket();

            if (packet != null) {
                this.broadcast(players, packet);
            }
        }

    }

    private void broadcast(List<ServerPlayer> players, Packet<?> packet) {
        players.forEach((entityplayer) -> {
            entityplayer.connection.send(packet);
        });
    }

    @Override
    public int getTicketLevel() {
        return this.newChunkHolder.getTicketLevel(); // Paper - rewrite chunk system
    }

    @Override
    public int getQueueLevel() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void setQueueLevel(int level) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void setTicketLevel(int level) {
        // Paper - rewrite chunk system
    }

    private void scheduleFullChunkPromotion(ChunkMap chunkLoadingManager, CompletableFuture<ChunkResult<LevelChunk>> chunkFuture, Executor executor, FullChunkStatus target) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void demoteFullChunk(ChunkMap chunkLoadingManager, FullChunkStatus target) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected void updateFutures(ChunkMap chunkLoadingManager, Executor executor) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean wasAccessibleSinceLastSave() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void refreshAccessibility() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @FunctionalInterface
    public interface LevelChangeListener {

        void onLevelChange(ChunkPos pos, IntSupplier levelGetter, int targetLevel, IntConsumer levelSetter);
    }

    public interface PlayerProvider {

        List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge);
    }
}
