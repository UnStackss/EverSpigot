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

public class ChunkHolder extends GenerationChunkHolder {

    public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
    private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    private final LevelHeightAccessor levelHeightAccessor;
    private volatile CompletableFuture<ChunkResult<LevelChunk>> fullChunkFuture; private int fullChunkCreateCount; private volatile boolean isFullChunkReady; // Paper - cache chunk ticking stage
    private volatile CompletableFuture<ChunkResult<LevelChunk>> tickingChunkFuture; private volatile boolean isTickingReady; // Paper - cache chunk ticking stage
    private volatile CompletableFuture<ChunkResult<LevelChunk>> entityTickingChunkFuture; private volatile boolean isEntityTickingReady; // Paper - cache chunk ticking stage
    public int oldTicketLevel;
    private int ticketLevel;
    private int queueLevel;
    private boolean hasChangedSections;
    private final ShortSet[] changedBlocksPerSection;
    private final BitSet blockChangedLightSectionFilter;
    private final BitSet skyChangedLightSectionFilter;
    private final LevelLightEngine lightEngine;
    private final ChunkHolder.LevelChangeListener onLevelChange;
    public final ChunkHolder.PlayerProvider playerProvider;
    private boolean wasAccessibleSinceLastSave;
    private CompletableFuture<?> pendingFullStateConfirmation;
    private CompletableFuture<?> sendSync;
    private CompletableFuture<?> saveSync;

    public ChunkHolder(ChunkPos pos, int level, LevelHeightAccessor world, LevelLightEngine lightingProvider, ChunkHolder.LevelChangeListener levelUpdateListener, ChunkHolder.PlayerProvider playersWatchingChunkProvider) {
        super(pos);
        this.fullChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.tickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.entityTickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.blockChangedLightSectionFilter = new BitSet();
        this.skyChangedLightSectionFilter = new BitSet();
        this.pendingFullStateConfirmation = CompletableFuture.completedFuture(null); // CraftBukkit - decompile error
        this.sendSync = CompletableFuture.completedFuture(null); // CraftBukkit - decompile error
        this.saveSync = CompletableFuture.completedFuture(null); // CraftBukkit - decompile error
        this.levelHeightAccessor = world;
        this.lightEngine = lightingProvider;
        this.onLevelChange = levelUpdateListener;
        this.playerProvider = playersWatchingChunkProvider;
        this.oldTicketLevel = ChunkLevel.MAX_LEVEL + 1;
        this.ticketLevel = this.oldTicketLevel;
        this.queueLevel = this.oldTicketLevel;
        this.setTicketLevel(level);
        this.changedBlocksPerSection = new ShortSet[world.getSectionsCount()];
    }

    // CraftBukkit start
    public LevelChunk getFullChunkNow() {
        // Note: We use the oldTicketLevel for isLoaded checks.
        if (!ChunkLevel.fullStatus(this.oldTicketLevel).isOrAfter(FullChunkStatus.FULL)) return null;
        return this.getFullChunkNowUnchecked();
    }

    public LevelChunk getFullChunkNowUnchecked() {
        return (LevelChunk) this.getChunkIfPresentUnchecked(ChunkStatus.FULL);
    }
    // CraftBukkit end

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
        return this.tickingChunkFuture;
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
        return this.entityTickingChunkFuture;
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
        return this.fullChunkFuture;
    }

    @Nullable
    public final LevelChunk getTickingChunk() { // Paper - final for inline
        return (LevelChunk) ((ChunkResult) this.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).orElse(null); // CraftBukkit - decompile error
    }

    @Nullable
    public LevelChunk getChunkToSend() {
        return !this.sendSync.isDone() ? null : this.getTickingChunk();
    }

    public CompletableFuture<?> getSendSyncFuture() {
        return this.sendSync;
    }

    public void addSendDependency(CompletableFuture<?> postProcessingFuture) {
        if (this.sendSync.isDone()) {
            this.sendSync = postProcessingFuture;
        } else {
            this.sendSync = this.sendSync.thenCombine(postProcessingFuture, (object, object1) -> {
                return null;
            });
        }

    }

    public CompletableFuture<?> getSaveSyncFuture() {
        return this.saveSync;
    }

    public boolean isReadyForSaving() {
        return this.getGenerationRefCount() == 0 && this.saveSync.isDone();
    }

    private void addSaveDependency(CompletableFuture<?> savingFuture) {
        if (this.saveSync.isDone()) {
            this.saveSync = savingFuture;
        } else {
            this.saveSync = this.saveSync.thenCombine(savingFuture, (object, object1) -> {
                return null;
            });
        }

    }

    public void blockChanged(BlockPos pos) {
        LevelChunk chunk = this.getTickingChunk();

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
            LevelChunk chunk = this.getTickingChunk();

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
                list = this.playerProvider.getPlayers(this.pos, true);
                if (!list.isEmpty()) {
                    ClientboundLightUpdatePacket packetplayoutlightupdate = new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter);

                    this.broadcast(list, packetplayoutlightupdate);
                }

                this.skyChangedLightSectionFilter.clear();
                this.blockChangedLightSectionFilter.clear();
            }

            if (this.hasChangedSections) {
                list = this.playerProvider.getPlayers(this.pos, false);

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
        return this.ticketLevel;
    }

    @Override
    public int getQueueLevel() {
        return this.queueLevel;
    }

    private void setQueueLevel(int level) {
        this.queueLevel = level;
    }

    public void setTicketLevel(int level) {
        this.ticketLevel = level;
    }

    private void scheduleFullChunkPromotion(ChunkMap chunkLoadingManager, CompletableFuture<ChunkResult<LevelChunk>> chunkFuture, Executor executor, FullChunkStatus target) {
        this.pendingFullStateConfirmation.cancel(false);
        CompletableFuture<Void> completablefuture1 = new CompletableFuture();

        completablefuture1.thenRunAsync(() -> {
            chunkLoadingManager.onFullChunkStatusChange(this.pos, target);
        }, executor);
        this.pendingFullStateConfirmation = completablefuture1;
        chunkFuture.thenAccept((chunkresult) -> {
            chunkresult.ifSuccess((chunk) -> {
                completablefuture1.complete(null); // CraftBukkit - decompile error
            });
        });
    }

    private void demoteFullChunk(ChunkMap chunkLoadingManager, FullChunkStatus target) {
        this.pendingFullStateConfirmation.cancel(false);
        chunkLoadingManager.onFullChunkStatusChange(this.pos, target);
    }

    protected void updateFutures(ChunkMap chunkLoadingManager, Executor executor) {
        FullChunkStatus fullchunkstatus = ChunkLevel.fullStatus(this.oldTicketLevel);
        FullChunkStatus fullchunkstatus1 = ChunkLevel.fullStatus(this.ticketLevel);
        boolean flag = fullchunkstatus.isOrAfter(FullChunkStatus.FULL);
        boolean flag1 = fullchunkstatus1.isOrAfter(FullChunkStatus.FULL);
        // CraftBukkit start
        // ChunkUnloadEvent: Called before the chunk is unloaded: isChunkLoaded is still true and chunk can still be modified by plugins.
        if (flag && !flag1) {
            this.getFullChunkFuture().thenAccept((either) -> {
                LevelChunk chunk = (LevelChunk) either.orElse(null);
                if (chunk != null) {
                    chunkLoadingManager.callbackExecutor.execute(() -> {
                        // Minecraft will apply the chunks tick lists to the world once the chunk got loaded, and then store the tick
                        // lists again inside the chunk once the chunk becomes inaccessible and set the chunk's needsSaving flag.
                        // These actions may however happen deferred, so we manually set the needsSaving flag already here.
                        chunk.setUnsaved(true);
                        chunk.unloadCallback();
                    });
                }
            }).exceptionally((throwable) -> {
                // ensure exceptions are printed, by default this is not the case
                MinecraftServer.LOGGER.error("Failed to schedule unload callback for chunk " + ChunkHolder.this.pos, throwable);
                return null;
            });

            // Run callback right away if the future was already done
            chunkLoadingManager.callbackExecutor.run();
        }
        // CraftBukkit end

        this.wasAccessibleSinceLastSave |= flag1;
        if (!flag && flag1) {
            int expectCreateCount = ++this.fullChunkCreateCount; // Paper
            this.fullChunkFuture = chunkLoadingManager.prepareAccessibleChunk(this);
            this.scheduleFullChunkPromotion(chunkLoadingManager, this.fullChunkFuture, executor, FullChunkStatus.FULL);
            // Paper start - cache ticking ready status
            this.fullChunkFuture.thenAccept(chunkResult -> {
                chunkResult.ifSuccess(chunk -> {
                    if (ChunkHolder.this.fullChunkCreateCount == expectCreateCount) {
                        ChunkHolder.this.isFullChunkReady = true;
                        ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkBorder(chunk, this);
                    }
                });
            });
            // Paper end - cache ticking ready status
            this.addSaveDependency(this.fullChunkFuture);
        }

        if (flag && !flag1) {
            // Paper start
            if (this.isFullChunkReady) {
                ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkNotBorder(this.fullChunkFuture.join().orElseThrow(IllegalStateException::new), this); // Paper
            }
            // Paper end
            this.fullChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
            this.fullChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean flag2 = fullchunkstatus.isOrAfter(FullChunkStatus.BLOCK_TICKING);
        boolean flag3 = fullchunkstatus1.isOrAfter(FullChunkStatus.BLOCK_TICKING);

        if (!flag2 && flag3) {
            this.tickingChunkFuture = chunkLoadingManager.prepareTickingChunk(this);
            this.scheduleFullChunkPromotion(chunkLoadingManager, this.tickingChunkFuture, executor, FullChunkStatus.BLOCK_TICKING);
            // Paper start - cache ticking ready status
            this.tickingChunkFuture.thenAccept(chunkResult -> {
                chunkResult.ifSuccess(chunk -> {
                    // note: Here is a very good place to add callbacks to logic waiting on this.
                    ChunkHolder.this.isTickingReady = true;
                    ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkTicking(chunk, this);
                });
            });
            // Paper end
            this.addSaveDependency(this.tickingChunkFuture);
        }

        if (flag2 && !flag3) {
            // Paper start
            if (this.isTickingReady) {
                ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkNotTicking(this.tickingChunkFuture.join().orElseThrow(IllegalStateException::new), this); // Paper
            }
            // Paper end
            this.tickingChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK); this.isTickingReady = false; // Paper - cache chunk ticking stage
            this.tickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean flag4 = fullchunkstatus.isOrAfter(FullChunkStatus.ENTITY_TICKING);
        boolean flag5 = fullchunkstatus1.isOrAfter(FullChunkStatus.ENTITY_TICKING);

        if (!flag4 && flag5) {
            if (this.entityTickingChunkFuture != ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException());
            }

            this.entityTickingChunkFuture = chunkLoadingManager.prepareEntityTickingChunk(this);
            this.scheduleFullChunkPromotion(chunkLoadingManager, this.entityTickingChunkFuture, executor, FullChunkStatus.ENTITY_TICKING);
            // Paper start - cache ticking ready status
            this.entityTickingChunkFuture.thenAccept(chunkResult -> {
                chunkResult.ifSuccess(chunk -> {
                    ChunkHolder.this.isEntityTickingReady = true;
                    ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkEntityTicking(chunk, this);
                });
            });
            // Paper end
            this.addSaveDependency(this.entityTickingChunkFuture);
        }

        if (flag4 && !flag5) {
            // Paper start
            if (this.isEntityTickingReady) {
                ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkNotEntityTicking(this.entityTickingChunkFuture.join().orElseThrow(IllegalStateException::new), this);
            }
            // Paper end
            this.entityTickingChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK); this.isEntityTickingReady = false; // Paper - cache chunk ticking stage
            this.entityTickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        if (!fullchunkstatus1.isOrAfter(fullchunkstatus)) {
            this.demoteFullChunk(chunkLoadingManager, fullchunkstatus1);
        }

        this.onLevelChange.onLevelChange(this.pos, this::getQueueLevel, this.ticketLevel, this::setQueueLevel);
        this.oldTicketLevel = this.ticketLevel;
        // CraftBukkit start
        // ChunkLoadEvent: Called after the chunk is loaded: isChunkLoaded returns true and chunk is ready to be modified by plugins.
        if (!fullchunkstatus.isOrAfter(FullChunkStatus.FULL) && fullchunkstatus1.isOrAfter(FullChunkStatus.FULL)) {
            this.getFullChunkFuture().thenAccept((either) -> {
                LevelChunk chunk = (LevelChunk) either.orElse(null);
                if (chunk != null) {
                    chunkLoadingManager.callbackExecutor.execute(() -> {
                        chunk.loadCallback();
                    });
                }
            }).exceptionally((throwable) -> {
                // ensure exceptions are printed, by default this is not the case
                MinecraftServer.LOGGER.error("Failed to schedule load callback for chunk " + ChunkHolder.this.pos, throwable);
                return null;
            });

            // Run callback right away if the future was already done
            chunkLoadingManager.callbackExecutor.run();
        }
        // CraftBukkit end
    }

    public boolean wasAccessibleSinceLastSave() {
        return this.wasAccessibleSinceLastSave;
    }

    public void refreshAccessibility() {
        this.wasAccessibleSinceLastSave = ChunkLevel.fullStatus(this.ticketLevel).isOrAfter(FullChunkStatus.FULL);
    }

    @FunctionalInterface
    public interface LevelChangeListener {

        void onLevelChange(ChunkPos pos, IntSupplier levelGetter, int targetLevel, IntConsumer levelSetter);
    }

    public interface PlayerProvider {

        List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge);
    }
}
