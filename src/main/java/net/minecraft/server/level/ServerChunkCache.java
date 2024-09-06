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

public class ServerChunkCache extends ChunkSource {

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
        if (Thread.currentThread() != this.mainThread) {
            return (ChunkAccess) CompletableFuture.supplyAsync(() -> {
                return this.getChunk(x, z, leastStatus, create);
            }, this.mainThreadProcessor).join();
        } else {
            ProfilerFiller gameprofilerfiller = this.level.getProfiler();

            gameprofilerfiller.incrementCounter("getChunk");
            long k = ChunkPos.asLong(x, z);

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && leastStatus == this.lastChunkStatus[l]) {
                    ChunkAccess ichunkaccess = this.lastChunk[l];

                    if (ichunkaccess != null) { // CraftBukkit - the chunk can become accessible in the meantime TODO for non-null chunks it might also make sense to check that the chunk's state hasn't changed in the meantime
                        return ichunkaccess;
                    }
                }
            }

            gameprofilerfiller.incrementCounter("getChunkCacheMiss");
            this.level.timings.syncChunkLoadTimer.startTiming(); // Spigot
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.getChunkFutureMainThread(x, z, leastStatus, create);
            ServerChunkCache.MainThreadExecutor chunkproviderserver_b = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            chunkproviderserver_b.managedBlock(completablefuture::isDone);
            this.level.timings.syncChunkLoadTimer.stopTiming(); // Spigot
            ChunkResult<ChunkAccess> chunkresult = (ChunkResult) completablefuture.join();
            ChunkAccess ichunkaccess1 = (ChunkAccess) chunkresult.orElse(null); // CraftBukkit - decompile error

            if (ichunkaccess1 == null && create) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + chunkresult.getError()));
            } else {
                this.storeInCache(k, ichunkaccess1, leastStatus);
                return ichunkaccess1;
            }
        }
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int chunkX, int chunkZ) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        } else {
            this.level.getProfiler().incrementCounter("getChunkNow");
            long k = ChunkPos.asLong(chunkX, chunkZ);

            ChunkAccess ichunkaccess;

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && this.lastChunkStatus[l] == ChunkStatus.FULL) {
                    ichunkaccess = this.lastChunk[l];
                    return ichunkaccess instanceof LevelChunk ? (LevelChunk) ichunkaccess : null;
                }
            }

            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

            if (playerchunk == null) {
                return null;
            } else {
                ichunkaccess = playerchunk.getChunkIfPresent(ChunkStatus.FULL);
                if (ichunkaccess != null) {
                    this.storeInCache(k, ichunkaccess, ChunkStatus.FULL);
                    if (ichunkaccess instanceof LevelChunk) {
                        return (LevelChunk) ichunkaccess;
                    }
                }

                return null;
            }
        }
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
        ChunkPos chunkcoordintpair = new ChunkPos(chunkX, chunkZ);
        long k = chunkcoordintpair.toLong();
        int l = ChunkLevel.byStatus(leastStatus);
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

        // CraftBukkit start - don't add new ticket for currently unloading chunk
        boolean currentlyUnloading = false;
        if (playerchunk != null) {
            FullChunkStatus oldChunkState = ChunkLevel.fullStatus(playerchunk.oldTicketLevel);
            FullChunkStatus currentChunkState = ChunkLevel.fullStatus(playerchunk.getTicketLevel());
            currentlyUnloading = (oldChunkState.isOrAfter(FullChunkStatus.FULL) && !currentChunkState.isOrAfter(FullChunkStatus.FULL));
        }
        if (create && !currentlyUnloading) {
            // CraftBukkit end
            this.distanceManager.addTicket(TicketType.UNKNOWN, chunkcoordintpair, l, chunkcoordintpair);
            if (this.chunkAbsent(playerchunk, l)) {
                ProfilerFiller gameprofilerfiller = this.level.getProfiler();

                gameprofilerfiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                playerchunk = this.getVisibleChunkIfPresent(k);
                gameprofilerfiller.pop();
                if (this.chunkAbsent(playerchunk, l)) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(playerchunk, l) ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE : playerchunk.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
    }

    private boolean chunkAbsent(@Nullable ChunkHolder holder, int maxLevel) {
        return holder == null || holder.oldTicketLevel > maxLevel; // CraftBukkit using oldTicketLevel for isLoaded checks
    }

    @Override
    public boolean hasChunk(int x, int z) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent((new ChunkPos(x, z)).toLong());
        int k = ChunkLevel.byStatus(ChunkStatus.FULL);

        return !this.chunkAbsent(playerchunk, k);
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        long k = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

        return playerchunk == null ? null : playerchunk.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    boolean runDistanceManagerUpdates() {
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = this.chunkMap.promoteChunkMap();

        this.chunkMap.runGenerationTasks();
        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCache();
            return true;
        }
    }

    public boolean isPositionTicking(long pos) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos);

        return playerchunk == null ? false : (!this.level.shouldTickBlocksAt(pos) ? false : ((ChunkResult) playerchunk.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).isSuccess());
    }

    public void save(boolean flush) {
        this.runDistanceManagerUpdates();
        this.chunkMap.saveAllChunks(flush);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        if (save) {
            this.save(true);
        }
        // CraftBukkit end
        this.lightEngine.close();
        this.chunkMap.close();
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
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
            this.tickChunks();
            this.level.timings.tracker.startTiming(); // Spigot
            this.chunkMap.tick();
            this.level.timings.tracker.stopTiming(); // Spigot
        }

        this.level.timings.doChunkUnload.startTiming(); // Spigot
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(shouldKeepTicking);
        this.level.timings.doChunkUnload.stopTiming(); // Spigot
        this.level.getProfiler().pop();
        this.clearCache();
    }

    private void tickChunks() {
        long i = this.level.getGameTime();
        long j = i - this.lastInhabitedUpdate;

        this.lastInhabitedUpdate = i;
        if (!this.level.isDebug()) {
            ProfilerFiller gameprofilerfiller = this.level.getProfiler();

            gameprofilerfiller.push("pollingChunks");
            gameprofilerfiller.push("filteringLoadedChunks");
            List<ServerChunkCache.ChunkAndHolder> list = Lists.newArrayListWithCapacity(this.chunkMap.size());
            Iterator iterator = this.chunkMap.getChunks().iterator();

            while (iterator.hasNext()) {
                ChunkHolder playerchunk = (ChunkHolder) iterator.next();
                LevelChunk chunk = playerchunk.getTickingChunk();

                if (chunk != null) {
                    list.add(new ServerChunkCache.ChunkAndHolder(chunk, playerchunk));
                }
            }

            if (this.level.tickRateManager().runsNormally()) {
                gameprofilerfiller.popPush("naturalSpawnCount");
                int k = this.distanceManager.getNaturalSpawnChunkCount();
                NaturalSpawner.SpawnState spawnercreature_d = NaturalSpawner.createState(k, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));

                this.lastSpawnState = spawnercreature_d;
                gameprofilerfiller.popPush("spawnAndTick");
                boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && !this.level.players().isEmpty(); // CraftBukkit

                Util.shuffle(list, this.level.random);
                int l = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
                boolean flag1 = this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) != 0L && this.level.getLevelData().getGameTime() % this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) == 0L; // CraftBukkit
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    ServerChunkCache.ChunkAndHolder chunkproviderserver_a = (ServerChunkCache.ChunkAndHolder) iterator1.next();
                    LevelChunk chunk1 = chunkproviderserver_a.chunk;
                    ChunkPos chunkcoordintpair = chunk1.getPos();

                    if (this.level.isNaturalSpawningAllowed(chunkcoordintpair) && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkcoordintpair)) {
                        chunk1.incrementInhabitedTime(j);
                        if (flag && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(chunkcoordintpair) && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkcoordintpair, true)) { // Spigot
                            NaturalSpawner.spawnForChunk(this.level, chunk1, spawnercreature_d, this.spawnFriendlies, this.spawnEnemies, flag1);
                        }

                        if (this.level.shouldTickBlocksAt(chunkcoordintpair.toLong())) {
                            this.level.timings.doTickTiles.startTiming(); // Spigot
                            this.level.tickChunk(chunk1, l);
                            this.level.timings.doTickTiles.stopTiming(); // Spigot
                        }
                    }
                }

                gameprofilerfiller.popPush("customSpawners");
                if (flag) {
                    this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                }
            }

            gameprofilerfiller.popPush("broadcast");
            list.forEach((chunkproviderserver_a1) -> {
                chunkproviderserver_a1.holder.broadcastChanges(chunkproviderserver_a1.chunk);
            });
            gameprofilerfiller.pop();
            gameprofilerfiller.pop();
        }
    }

    private void getFullChunk(long pos, Consumer<LevelChunk> chunkConsumer) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos);

        if (playerchunk != null) {
            ((ChunkResult) playerchunk.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).ifSuccess(chunkConsumer);
        }

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
        try {
            if (ServerChunkCache.this.runDistanceManagerUpdates()) {
                return true;
            } else {
                ServerChunkCache.this.lightEngine.tryScheduleUpdate();
                return super.pollTask();
            }
        } finally {
            ServerChunkCache.this.chunkMap.callbackExecutor.run();
        }
        // CraftBukkit end
        }
    }

    private static record ChunkAndHolder(LevelChunk chunk, ChunkHolder holder) {

    }
}
