package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;

public class ServerChunkCache extends ChunkSource implements ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemServerChunkCache { // Paper - rewrite chunk system

    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger(); // Paper
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private final DistanceManager distanceManager;
    final ServerLevel level;
    public final Thread mainThread;
    final ThreadedLevelLightEngine lightEngine;
    public final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    private long lastInhabitedUpdate;
    public boolean spawnEnemies = true;
    public boolean spawnFriendlies = true;
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final ChunkAccess[] lastChunk = new ChunkAccess[4];
    @Nullable
    @VisibleForDebug
    private NaturalSpawner.SpawnState lastSpawnState;
    // Paper start
    private final ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<net.minecraft.world.level.chunk.LevelChunk> fullChunks = new ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<>();
    long chunkFutureAwaitCounter;
    // Paper end
    // Paper start - rewrite chunk system

    @Override
    public final void moonrise$setFullChunk(final int chunkX, final int chunkZ, final LevelChunk chunk) {
        final long key = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ);
        if (chunk == null) {
            this.fullChunks.remove(key);
        } else {
            this.fullChunks.put(key, chunk);
        }
    }

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    private ChunkAccess syncLoad(final int chunkX, final int chunkZ, final ChunkStatus toStatus) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler();
        final CompletableFuture<ChunkAccess> completable = new CompletableFuture<>();
        chunkTaskScheduler.scheduleChunkLoad(
            chunkX, chunkZ, toStatus, true, ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.BLOCKING,
            completable::complete
        );

        if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, chunkX, chunkZ)) {
            ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.pushChunkWait(this.level, chunkX, chunkZ);
            this.mainThreadProcessor.managedBlock(completable::isDone);
            ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.popChunkWait();
        }

        final ChunkAccess ret = completable.join();
        if (ret == null) {
            throw new IllegalStateException("Chunk not loaded when requested");
        }

        return ret;
    }

    private ChunkAccess getChunkFallback(final int chunkX, final int chunkZ, final ChunkStatus toStatus,
                                         final boolean load) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler();
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder currentChunk = chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));

        final ChunkAccess ifPresent = currentChunk == null ? null : currentChunk.getChunkIfPresent(toStatus);

        if (ifPresent != null && (toStatus != ChunkStatus.FULL || currentChunk.isFullChunkReady())) {
            return ifPresent;
        }

        return load ? this.syncLoad(chunkX, chunkZ, toStatus) : null;
    }
    // Paper end - rewrite chunk system
    private ServerChunkCache.ChunkAndHolder[] iterationCopy; // Paper - chunk tick iteration optimisations

    public ServerChunkCache(ServerLevel world, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor workerExecutor, ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, ChunkProgressListener worldGenerationProgressListener, ChunkStatusUpdateListener chunkStatusChangeListener, Supplier<DimensionDataStorage> persistentStateManagerFactory) {
        this.level = world;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(world);
        this.mainThread = Thread.currentThread();
        File file = session.getDimensionPath(world.dimension()).resolve("data").toFile();

        file.mkdirs();
        this.dataStorage = new DimensionDataStorage(file, dataFixer, world.registryAccess());
        this.chunkMap = new ChunkMap(world, session, dataFixer, structureTemplateManager, workerExecutor, this.mainThreadProcessor, this, chunkGenerator, worldGenerationProgressListener, chunkStatusChangeListener, persistentStateManagerFactory, viewDistance, dsync);
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(simulationDistance);
        this.clearCache();
    }

    // CraftBukkit start - properly implement isChunkLoaded
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        ChunkHolder chunk = this.chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        return chunk.getFullChunkNow() != null;
    }
    // CraftBukkit end
    // Paper start
    // Paper - rewrite chunk system

    @Nullable
    public ChunkAccess getChunkAtImmediately(int x, int z) {
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) {
            return null;
        }

        return holder.getLatestChunk();
    }

    public <T> void addTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkPos, int ticketLevel, T identifier) {
        this.distanceManager.addTicket(ticketType, chunkPos, ticketLevel, identifier);
    }

    public <T> void removeTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkPos, int ticketLevel, T identifier) {
        this.distanceManager.removeTicket(ticketType, chunkPos, ticketLevel, identifier);
    }

    // "real" get chunk if loaded
    // Note: Partially copied from the getChunkAt method below
    @Nullable
    public LevelChunk getChunkAtIfCachedImmediately(int x, int z) {
        long k = ChunkPos.asLong(x, z);

        // Note: Bypass cache since we need to check ticket level, and to make this MT-Safe

        ChunkHolder playerChunk = this.getVisibleChunkIfPresent(k);
        if (playerChunk == null) {
            return null;
        }

        return playerChunk.getFullChunkNowUnchecked();
    }

    @Nullable
    public LevelChunk getChunkAtIfLoadedImmediately(int x, int z) {
        return this.fullChunks.get(ChunkPos.asLong(x, z));
    }
    // Paper end

    @Override
    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long pos) {
        return this.chunkMap.getVisibleChunkIfPresent(pos);
    }

    public int getTickingGenerated() {
        return this.chunkMap.getTickingGenerated();
    }

    private void storeInCache(long pos, @Nullable ChunkAccess chunk, ChunkStatus status) {
        for (int j = 3; j > 0; --j) {
            this.lastChunkPos[j] = this.lastChunkPos[j - 1];
            this.lastChunkStatus[j] = this.lastChunkStatus[j - 1];
            this.lastChunk[j] = this.lastChunk[j - 1];
        }

        this.lastChunkPos[0] = pos;
        this.lastChunkStatus[0] = status;
        this.lastChunk[0] = chunk;
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus leastStatus, boolean create) {
        // Paper start - rewrite chunk system
        if (leastStatus == ChunkStatus.FULL) {
            final LevelChunk ret = this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(x, z));

            if (ret != null) {
                return ret;
            }

            return create ? this.getChunkFallback(x, z, leastStatus, create) : null;
        }

        return this.getChunkFallback(x, z, leastStatus, create);
        // Paper end - rewrite chunk system
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int chunkX, int chunkZ) {
        return this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ)); // Paper - rewrite chunk system
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, (Object) null);
        Arrays.fill(this.lastChunk, (Object) null);
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        boolean flag1 = Thread.currentThread() == this.mainThread;
        CompletableFuture completablefuture;

        if (flag1) {
            completablefuture = this.getChunkFutureMainThread(chunkX, chunkZ, leastStatus, create);
            ServerChunkCache.MainThreadExecutor chunkproviderserver_b = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            chunkproviderserver_b.managedBlock(completablefuture::isDone);
        } else {
            completablefuture = CompletableFuture.supplyAsync(() -> {
                return this.getChunkFutureMainThread(chunkX, chunkZ, leastStatus, create);
            }, this.mainThreadProcessor).thenCompose((completablefuture1) -> {
                return completablefuture1;
            });
        }

        return completablefuture;
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        // Paper start - rewrite chunk system
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.level, chunkX, chunkZ, "Scheduling chunk load off-main");

        final int minLevel = ChunkLevel.byStatus(leastStatus);
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);

        final boolean needsFullScheduling = leastStatus == ChunkStatus.FULL && (chunkHolder == null || !chunkHolder.getChunkStatus().isOrAfter(FullChunkStatus.FULL));

        if ((chunkHolder == null || chunkHolder.getTicketLevel() > minLevel || needsFullScheduling) && !create) {
            return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        }

        final ChunkAccess ifPresent = chunkHolder == null ? null : chunkHolder.getChunkIfPresent(leastStatus);
        if (needsFullScheduling || ifPresent == null) {
            // schedule
            final CompletableFuture<ChunkResult<ChunkAccess>> ret = new CompletableFuture<>();
            final Consumer<ChunkAccess> complete = (ChunkAccess chunk) -> {
                if (chunk == null) {
                    ret.complete(ChunkHolder.UNLOADED_CHUNK);
                } else {
                    ret.complete(ChunkResult.of(chunk));
                }
            };

            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
                chunkX, chunkZ, leastStatus, true,
                ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER,
                complete
            );

            return ret;
        } else {
            // can return now
            return CompletableFuture.completedFuture(ChunkResult.of(ifPresent));
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return this.getChunkNow(x, z) != null; // Paper - rewrite chunk system
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        return newChunkHolder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
        // Paper end - rewrite chunk system
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    public boolean runDistanceManagerUpdates() { // Paper - public
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates(); // Paper - rewrite chunk system
    }

    // Paper start
    public boolean isPositionTicking(Entity entity) {
        return this.isPositionTicking(ChunkPos.asLong(net.minecraft.util.Mth.floor(entity.getX()) >> 4, net.minecraft.util.Mth.floor(entity.getZ()) >> 4));
    }
    // Paper end

    public boolean isPositionTicking(long pos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(pos);
        return newChunkHolder != null && newChunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    public void save(boolean flush) {
        // Paper - rewrite chunk system
        try (co.aikar.timings.Timing timed = level.timings.chunkSaveData.startTiming()) { // Paper - Timings
        this.chunkMap.saveAllChunks(flush);
        } // Paper - Timings
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.close(save, true); // Paper - rewrite chunk system
        // Paper start - Write SavedData IO async
        try {
            this.dataStorage.close();
        } catch (final IOException e) {
            LOGGER.error("Failed to close persistent world data", e);
        }
        // Paper end - Write SavedData IO async
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        if (true) return; // Paper - rewrite chunk system
        this.level.getProfiler().push("purge");
        this.distanceManager.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(() -> true);
        this.level.getProfiler().pop();
        this.clearCache();
    }
    // CraftBukkit end

    @Override
    public void tick(BooleanSupplier shouldKeepTicking, boolean tickChunks) {
        this.level.getProfiler().push("purge");
        this.level.timings.doChunkMap.startTiming(); // Spigot
        if (this.level.tickRateManager().runsNormally() || !tickChunks || this.level.spigotConfig.unloadFrozenChunks) { // Spigot
            this.distanceManager.purgeStaleTickets();
        }

        this.runDistanceManagerUpdates();
        this.level.timings.doChunkMap.stopTiming(); // Spigot
        this.level.getProfiler().popPush("chunks");
        if (tickChunks) {
            this.level.timings.chunks.startTiming(); // Paper - timings
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().tick(); // Paper - rewrite chunk system
            this.tickChunks();
            this.level.timings.chunks.stopTiming(); // Paper - timings
            this.chunkMap.tick();
        }

        this.level.timings.doChunkUnload.startTiming(); // Spigot
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(shouldKeepTicking);
        this.level.timings.doChunkUnload.stopTiming(); // Spigot
        this.level.getProfiler().pop();
        this.clearCache();
    }

    private void tickChunks() {
        long chunksTicked = 0; // Paper - rewrite chunk system
        long i = this.level.getGameTime();
        long j = i - this.lastInhabitedUpdate;

        this.lastInhabitedUpdate = i;
        if (!this.level.isDebug()) {
            ProfilerFiller gameprofilerfiller = this.level.getProfiler();

            gameprofilerfiller.push("pollingChunks");
            gameprofilerfiller.push("filteringLoadedChunks");
            // Paper start - chunk tick iteration optimisations
            List<ServerChunkCache.ChunkAndHolder> list;
            {
                final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> tickingChunks =
                    ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel) this.level).moonrise$getTickingChunks();

                final ServerChunkCache.ChunkAndHolder[] raw = tickingChunks.getRawDataUnchecked();
                final int size = tickingChunks.size();

                if (this.iterationCopy == null || this.iterationCopy.length < size) {
                    this.iterationCopy = new ServerChunkCache.ChunkAndHolder[raw.length];
                }
                System.arraycopy(raw, 0, this.iterationCopy, 0, size);

                list = it.unimi.dsi.fastutil.objects.ObjectArrayList.wrap(
                    this.iterationCopy, size
                );
            }
            // Paper end - chunk tick iteration optimisations
            Iterator iterator = null; // Paper - chunk tick iteration optimisations
            if (this.level.getServer().tickRateManager().runsNormally()) this.level.timings.chunkTicks.startTiming(); // Paper

            // Paper - chunk tick iteration optimisations

            if (this.level.tickRateManager().runsNormally()) {
                gameprofilerfiller.popPush("naturalSpawnCount");
                this.level.timings.countNaturalMobs.startTiming(); // Paper - timings
                int k = this.distanceManager.getNaturalSpawnChunkCount();
                // Paper start - Optional per player mob spawns
                int naturalSpawnChunkCount = k;
                NaturalSpawner.SpawnState spawnercreature_d; // moved down
                if ((this.spawnFriendlies || this.spawnEnemies) && this.level.paperConfig().entities.spawning.perPlayerMobSpawns) { // don't count mobs when animals and monsters are disabled
                    // re-set mob counts
                    for (ServerPlayer player : this.level.players) {
                        // Paper start - per player mob spawning backoff
                        for (int ii = 0; ii < ServerPlayer.MOBCATEGORY_TOTAL_ENUMS; ii++) {
                            player.mobCounts[ii] = 0;

                            int newBackoff = player.mobBackoffCounts[ii] - 1; // TODO make configurable bleed // TODO use nonlinear algorithm?
                            if (newBackoff < 0) {
                                newBackoff = 0;
                            }
                            player.mobBackoffCounts[ii] = newBackoff;
                        }
                        // Paper end - per player mob spawning backoff
                    }
                    spawnercreature_d = NaturalSpawner.createState(naturalSpawnChunkCount, this.level.getAllEntities(), this::getFullChunk, null, true);
                } else {
                    spawnercreature_d = NaturalSpawner.createState(naturalSpawnChunkCount, this.level.getAllEntities(), this::getFullChunk, !this.level.paperConfig().entities.spawning.perPlayerMobSpawns ? new LocalMobCapCalculator(this.chunkMap) : null, false);
                }
                // Paper end - Optional per player mob spawns
                this.level.timings.countNaturalMobs.stopTiming(); // Paper - timings

                this.lastSpawnState = spawnercreature_d;
                gameprofilerfiller.popPush("spawnAndTick");
                boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && !this.level.players().isEmpty(); // CraftBukkit

                if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) Util.shuffle(list, this.level.random); // Paper - per player mob spawns - do not need this when per-player is enabled
                // Paper start - PlayerNaturallySpawnCreaturesEvent
                int chunkRange = level.spigotConfig.mobSpawnRange;
                chunkRange = (chunkRange > level.spigotConfig.viewDistance) ? (byte) level.spigotConfig.viewDistance : chunkRange;
                chunkRange = Math.min(chunkRange, 8);
                for (ServerPlayer entityPlayer : this.level.players()) {
                    entityPlayer.playerNaturallySpawnedEvent = new com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent(entityPlayer.getBukkitEntity(), (byte) chunkRange);
                    entityPlayer.playerNaturallySpawnedEvent.callEvent();
                }
                // Paper end - PlayerNaturallySpawnCreaturesEvent
                int l = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
                boolean flag1 = this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) != 0L && this.level.getLevelData().getGameTime() % this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) == 0L; // CraftBukkit
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    ServerChunkCache.ChunkAndHolder chunkproviderserver_a = (ServerChunkCache.ChunkAndHolder) iterator1.next();
                    LevelChunk chunk1 = chunkproviderserver_a.chunk;
                    ChunkPos chunkcoordintpair = chunk1.getPos();

                    if (true && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkcoordintpair)) { // Paper - rewrite chunk system
                        chunk1.incrementInhabitedTime(j);
                        if (flag && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(chunkcoordintpair) && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkcoordintpair, true)) { // Spigot
                            NaturalSpawner.spawnForChunk(this.level, chunk1, spawnercreature_d, this.spawnFriendlies, this.spawnEnemies, flag1);
                        }

                        if (true) { // Paper - rewrite chunk system
                            this.level.tickChunk(chunk1, l);
                            // Paper start - rewrite chunk system
                            if ((++chunksTicked & 7L) == 0L) {
                                ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.level.getServer()).moonrise$executeMidTickTasks();
                            }
                            // Paper end - rewrite chunk system
                        }
                    }
                }
                this.level.timings.chunkTicks.stopTiming(); // Paper

                gameprofilerfiller.popPush("customSpawners");
                if (flag) {
                    try (co.aikar.timings.Timing ignored = this.level.timings.miscMobSpawning.startTiming()) { // Paper - timings
                    this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                    } // Paper - timings
                }
            }

            gameprofilerfiller.popPush("broadcast");
            // Paper start - chunk tick iteration optimisations
            this.level.timings.broadcastChunkUpdates.startTiming(); // Paper - timing
            {
                final it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> chunks = (it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder>)list;
                final ServerChunkCache.ChunkAndHolder[] raw = chunks.elements();
                final int size = chunks.size();

                Objects.checkFromToIndex(0, size, raw.length);
                for (int idx = 0; idx < size; ++idx) {
                    final ServerChunkCache.ChunkAndHolder holder = raw[idx];
                    raw[idx] = null;

                    holder.holder().broadcastChanges(holder.chunk());
                }
            }
            this.level.timings.broadcastChunkUpdates.stopTiming(); // Paper - timing
            // Paper end - chunk tick iteration optimisations
            gameprofilerfiller.pop();
            gameprofilerfiller.pop();
        }
    }

    private void getFullChunk(long pos, Consumer<LevelChunk> chunkConsumer) {
        // Paper start - rewrite chunk system
        final LevelChunk fullChunk = this.getChunkNow(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(pos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(pos));
        if (fullChunk != null) {
            chunkConsumer.accept(fullChunk);
        }
        // Paper end - rewrite chunk system

    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));

        if (playerchunk != null) {
            playerchunk.blockChanged(pos);
        }

    }

    @Override
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        this.mainThreadProcessor.execute(() -> {
            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos.chunk().toLong());

            if (playerchunk != null) {
                playerchunk.sectionLightChanged(type, pos.y());
            }

        });
    }

    public <T> void addRegionTicket(TicketType<T> ticketType, ChunkPos pos, int radius, T argument) {
        this.distanceManager.addRegionTicket(ticketType, pos, radius, argument);
    }

    public <T> void removeRegionTicket(TicketType<T> ticketType, ChunkPos pos, int radius, T argument) {
        this.distanceManager.removeRegionTicket(ticketType, pos, radius, argument);
    }

    @Override
    public void updateChunkForced(ChunkPos pos, boolean forced) {
        this.distanceManager.updateChunkForced(pos, forced);
    }

    public void move(ServerPlayer player) {
        if (!player.isRemoved()) {
            this.chunkMap.move(player);
        }

    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void broadcastAndSend(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcastAndSend(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int watchDistance) {
        this.chunkMap.setServerViewDistance(watchDistance);
    }

    // Paper start - rewrite chunk system
    public void setSendViewDistance(int viewDistance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().setSendDistance(viewDistance);
    }
    // Paper end - rewrite chunk system

    public void setSimulationDistance(int simulationDistance) {
        this.distanceManager.updateSimulationDistance(simulationDistance);
    }

    @Override
    public void setSpawnSettings(boolean spawnMonsters, boolean spawnAnimals) {
        this.spawnEnemies = spawnMonsters;
        this.spawnFriendlies = spawnAnimals;
    }

    public String getChunkDebugData(ChunkPos pos) {
        return this.chunkMap.getChunkDebugData(pos);
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @Nullable
    @VisibleForDebug
    public NaturalSpawner.SpawnState getLastSpawnState() {
        return this.lastSpawnState;
    }

    public void removeTicketsOnClosing() {
        this.distanceManager.removeTicketsOnClosing();
    }

    public final class MainThreadExecutor extends BlockableEventLoop<Runnable> {

        MainThreadExecutor(final Level world) {
            super("Chunk source main thread executor for " + String.valueOf(world.dimension().location()));
        }

        @Override
        public void managedBlock(BooleanSupplier stopCondition) {
            super.managedBlock(() -> {
                return MinecraftServer.throwIfFatalException() && stopCondition.getAsBoolean();
            });
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable task) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        @Override
        protected void doRunTask(Runnable task) {
            ServerChunkCache.this.level.getProfiler().incrementCounter("runTask");
            super.doRunTask(task);
        }

        @Override
        // CraftBukkit start - process pending Chunk loadCallback() and unloadCallback() after each run task
        public boolean pollTask() {
            // Paper start - rewrite chunk system
            final ServerChunkCache serverChunkCache = ServerChunkCache.this;
            if (serverChunkCache.runDistanceManagerUpdates()) {
                return true;
            } else {
                return super.pollTask() | ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)serverChunkCache.level).moonrise$getChunkTaskScheduler().executeMainThreadTask();
            }
            // Paper end - rewrite chunk system
        // CraftBukkit end
        }
    }

    public static record ChunkAndHolder(LevelChunk chunk, ChunkHolder holder) { // Paper - rewrite chunk system - public

    }
}
