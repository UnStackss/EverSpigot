package net.minecraft.server.level;

import co.aikar.timings.Timing; // Paper
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.ComparisonChain; // Paper
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
// CraftBukkit end

public class ChunkMap extends ChunkStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap {

    private static final ChunkResult<List<ChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<ChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(ChunkMap.UNLOADED_CHUNK_LIST_RESULT);
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    // Paper - rewrite chunk system
    public final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final Supplier<DimensionDataStorage> overworldDataStorage;
    private final PoiManager poiManager;
    public final LongSet toDrop;
    private boolean modified;
    // Paper - rewrite chunk system
    public final ChunkProgressListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    public final ChunkMap.ChunkDistanceManager distanceManager;
    public final AtomicInteger tickingGenerated; // Paper - public
    private final String storageName;
    private final PlayerMap playerMap;
    public final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;
    private final Long2ByteMap chunkTypeCache;
    private final Long2LongMap chunkSaveCooldowns;
    // Paper - rewrite chunk system
    public int serverViewDistance;
    public final WorldGenContext worldGenContext; // Paper - public

    // CraftBukkit start - recursion-safe executor for Chunk loadCallback() and unloadCallback()
    public final CallbackExecutor callbackExecutor = new CallbackExecutor();
    public static final class CallbackExecutor implements java.util.concurrent.Executor, Runnable {

        private final java.util.Queue<Runnable> queue = new java.util.ArrayDeque<>();

        @Override
        public void execute(Runnable runnable) {
            this.queue.add(runnable);
        }

        @Override
        public void run() {
            Runnable task;
            while ((task = this.queue.poll()) != null) {
                task.run();
            }
        }
    };
    // CraftBukkit end

    // Paper start
    public final ChunkHolder getUnloadingChunkHolder(int chunkX, int chunkZ) {
        return null; // Paper - rewrite chunk system
    }
    // Paper end

    public ChunkMap(ServerLevel world, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor executor, BlockableEventLoop<Runnable> mainThreadExecutor, LightChunkGetter chunkProvider, ChunkGenerator chunkGenerator, ChunkProgressListener worldGenerationProgressListener, ChunkStatusUpdateListener chunkStatusChangeListener, Supplier<DimensionDataStorage> persistentStateManagerFactory, int viewDistance, boolean dsync) {
        super(new RegionStorageInfo(session.getLevelId(), world.dimension(), "chunk"), session.getDimensionPath(world.dimension()).resolve("region"), dataFixer, dsync);
        // Paper - rewrite chunk system
        this.toDrop = new LongOpenHashSet();
        this.tickingGenerated = new AtomicInteger();
        this.playerMap = new PlayerMap();
        this.entityMap = new Int2ObjectOpenHashMap();
        this.chunkTypeCache = new Long2ByteOpenHashMap();
        this.chunkSaveCooldowns = new Long2LongOpenHashMap();
        // Paper - rewrite chunk system
        Path path = session.getDimensionPath(world.dimension());

        this.storageName = path.getFileName().toString();
        this.level = world;
        RegistryAccess iregistrycustom = world.registryAccess();
        long j = world.getSeed();

        // CraftBukkit start - SPIGOT-7051: It's a rigged game! Use delegate for random state creation, otherwise it is not so random.
        ChunkGenerator randomGenerator = chunkGenerator;
        if (randomGenerator instanceof CustomChunkGenerator customChunkGenerator) {
            randomGenerator = customChunkGenerator.getDelegate();
        }
        if (randomGenerator instanceof NoiseBasedChunkGenerator chunkgeneratorabstract) {
            // CraftBukkit end
            this.randomState = RandomState.create((NoiseGeneratorSettings) chunkgeneratorabstract.generatorSettings().value(), (HolderGetter) iregistrycustom.lookupOrThrow(Registries.NOISE), j);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), (HolderGetter) iregistrycustom.lookupOrThrow(Registries.NOISE), j);
        }

        this.chunkGeneratorState = chunkGenerator.createState(iregistrycustom.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, j, world.spigotConfig); // Spigot
        this.mainThreadExecutor = mainThreadExecutor;
        ProcessorMailbox<Runnable> threadedmailbox = ProcessorMailbox.create(executor, "worldgen");

        Objects.requireNonNull(mainThreadExecutor);
        ProcessorHandle<Runnable> mailbox = ProcessorHandle.of("main", mainThreadExecutor::tell);

        this.progressListener = worldGenerationProgressListener;
        this.chunkStatusListener = chunkStatusChangeListener;
        ProcessorMailbox<Runnable> threadedmailbox1 = ProcessorMailbox.create(executor, "light");

        // Paper - rewrite chunk system
        this.lightEngine = new ThreadedLevelLightEngine(chunkProvider, this, this.level.dimensionType().hasSkyLight(), threadedmailbox1, null); // Paper - rewrite chunk system
        this.distanceManager = new ChunkMap.ChunkDistanceManager(executor, mainThreadExecutor);
        this.overworldDataStorage = persistentStateManagerFactory;
        this.poiManager = new PoiManager(new RegionStorageInfo(session.getLevelId(), world.dimension(), "poi"), path.resolve("poi"), dataFixer, dsync, iregistrycustom, world.getServer(), world);
        this.setServerViewDistance(viewDistance);
        this.worldGenContext = new WorldGenContext(world, chunkGenerator, structureTemplateManager, this.lightEngine, null); // Paper - rewrite chunk system
    }

    // Paper start
    // Paper start - Optional per player mob spawns
    public void updatePlayerMobTypeMap(final Entity entity) {
        if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
            return;
        }
        final int index = entity.getType().getCategory().ordinal();

        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> inRange =
            this.level.moonrise$getNearbyPlayers().getPlayers(entity.chunkPosition(), ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
        if (inRange == null) {
            return;
        }
        final ServerPlayer[] backingSet = inRange.getRawDataUnchecked();
        for (int i = 0, len = inRange.size(); i < len; i++) {
            ++(backingSet[i].mobCounts[index]);
        }
    }
    public int getMobCountNear(final ServerPlayer player, final net.minecraft.world.entity.MobCategory mobCategory) {
        return player.mobCounts[mobCategory.ordinal()];
        // Paper end - Optional per player mob spawns
    }
    // Paper end

    protected ChunkGenerator generator() {
        return this.worldGenContext.generator();
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    private static double euclideanDistanceSquared(ChunkPos pos, Entity entity) {
        double d0 = (double) SectionPos.sectionToBlockCoord(pos.x, 8);
        double d1 = (double) SectionPos.sectionToBlockCoord(pos.z, 8);
        double d2 = d0 - entity.getX();
        double d3 = d1 - entity.getZ();

        return d2 * d2 + d3 * d3;
    }

    boolean isChunkTracked(ServerPlayer player, int chunkX, int chunkZ) {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().isChunkSent(player, chunkX, chunkZ); // Paper - rewrite chunk system
    }

    private boolean isChunkOnTrackedBorder(ServerPlayer player, int chunkX, int chunkZ) {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().isChunkSent(player, chunkX, chunkZ, true); // Paper - rewrite chunk system
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    protected ChunkHolder getUpdatingChunkIfPresent(long pos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(pos);
        return holder == null ? null : holder.vanillaChunkHolder;
        // Paper end - rewrite chunk system
    }

    @Nullable
    public ChunkHolder getVisibleChunkIfPresent(long pos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(pos);
        return holder == null ? null : holder.vanillaChunkHolder;
        // Paper end - rewrite chunk system
    }

    protected IntSupplier getChunkQueueLevel(long pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public String getChunkDebugData(ChunkPos chunkPos) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(chunkPos.toLong());

        if (playerchunk == null) {
            return "null";
        } else {
            String s = playerchunk.getTicketLevel() + "\n";
            ChunkStatus chunkstatus = playerchunk.getLatestStatus();
            ChunkAccess ichunkaccess = playerchunk.getLatestChunk();

            if (chunkstatus != null) {
                s = s + "St: \u00a7" + chunkstatus.getIndex() + String.valueOf(chunkstatus) + "\u00a7r\n";
            }

            if (ichunkaccess != null) {
                s = s + "Ch: \u00a7" + ichunkaccess.getPersistedStatus().getIndex() + String.valueOf(ichunkaccess.getPersistedStatus()) + "\u00a7r\n";
            }

            FullChunkStatus fullchunkstatus = playerchunk.getFullStatus();

            s = s + String.valueOf('\u00a7') + fullchunkstatus.ordinal() + String.valueOf(fullchunkstatus);
            return s + "\u00a7r";
        }
    }

    private CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(ChunkHolder centerChunk, int margin, IntFunction<ChunkStatus> distanceToStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details) {
        StringBuilder stringbuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = (playerchunk) -> {
            playerchunk.getAllFutures().forEach((pair) -> {
                ChunkStatus chunkstatus = (ChunkStatus) pair.getFirst();
                CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) pair.getSecond();

                if (completablefuture != null && completablefuture.isDone() && completablefuture.join() == null) {
                    stringbuilder.append(playerchunk.getPos()).append(" - status: ").append(chunkstatus).append(" future: ").append(completablefuture).append(System.lineSeparator());
                }

            });
        };

        stringbuilder.append("Updating:").append(System.lineSeparator());
        ca.spottedleaf.moonrise.common.util.ChunkSystem.getUpdatingChunkHolders(this.level).forEach(consumer); // Paper
        stringbuilder.append("Visible:").append(System.lineSeparator());
        ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolders(this.level).forEach(consumer); // Paper
        CrashReport crashreport = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Chunk loading");

        crashreportsystemdetails.setDetail("Details", (Object) details);
        crashreportsystemdetails.setDetail("Futures", (Object) stringbuilder);
        return new ReportedException(crashreport);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareEntityTickingChunk(ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Use ServerChunkCache#close"); // Paper - rewrite chunk system
    }

    protected void saveAllChunks(boolean flush) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.saveAllChunks(
            flush, false, false
        );
    }

    protected void tick(BooleanSupplier shouldKeepTicking) {
        ProfilerFiller gameprofilerfiller = this.level.getProfiler();

        gameprofilerfiller.push("poi");
        this.poiManager.tick(shouldKeepTicking);
        gameprofilerfiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(shouldKeepTicking);
        }

        gameprofilerfiller.pop();
    }

    public boolean hasWork() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void processUnloads(BooleanSupplier shouldKeepTicking) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.processUnloads(); // Paper - rewrite chunk system
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.autoSave(); // Paper - rewrite chunk system

    }

    private void scheduleUnload(long pos, ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected boolean promoteChunkMap() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private static boolean isChunkDataValid(CompoundTag nbt) {
        return nbt.contains("Status", 8);
    }

    private ChunkAccess handleChunkLoadFailure(Throwable throwable, ChunkPos chunkPos) {
        Throwable throwable1;

        if (throwable instanceof CompletionException completionexception) {
            throwable1 = completionexception.getCause();
        } else {
            throwable1 = throwable;
        }

        Throwable throwable2 = throwable1;

        if (throwable2 instanceof ReportedException reportedexception) {
            throwable1 = reportedexception.getCause();
        } else {
            throwable1 = throwable2;
        }

        Throwable throwable3 = throwable1;
        boolean flag = throwable3 instanceof Error;
        boolean flag1 = throwable3 instanceof IOException || throwable3 instanceof NbtException;

        if (!flag) {
            if (!flag1) {
                ;
            }

            this.level.getServer().reportChunkLoadFailure(throwable3, this.storageInfo(), chunkPos);
            return this.createEmptyChunk(chunkPos);
        } else {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception loading chunk");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Chunk being loaded");

            crashreportsystemdetails.setDetail("pos", (Object) chunkPos);
            this.markPositionReplaceable(chunkPos);
            throw new ReportedException(crashreport);
        }
    }

    private ChunkAccess createEmptyChunk(ChunkPos chunkPos) {
        this.markPositionReplaceable(chunkPos);
        return new ProtoChunk(chunkPos, UpgradeData.EMPTY, this.level, this.level.registryAccess().registryOrThrow(Registries.BIOME), (BlendingData) null);
    }

    private void markPositionReplaceable(ChunkPos pos) {
        this.chunkTypeCache.put(pos.toLong(), (byte) -1);
    }

    private byte markPosition(ChunkPos pos, ChunkType type) {
        return this.chunkTypeCache.put(pos.toLong(), (byte) (type == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder chunkHolder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunkHolder, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus requestedStatus, ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void runGenerationTask(ChunkGenerationTask chunkLoader) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void runGenerationTasks() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void onChunkReadyToSend(LevelChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system

    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean save(ChunkAccess chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private boolean isExistingChunkFull(ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void setServerViewDistance(int watchDistance) { // Paper - public
        // Paper start - rewrite chunk system
        final int clamped = Mth.clamp(watchDistance, 2, ca.spottedleaf.moonrise.common.util.MoonriseConstants.MAX_VIEW_DISTANCE);
        if (clamped == this.serverViewDistance) {
            return;
        }

        this.serverViewDistance = clamped;
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().setLoadDistance(this.serverViewDistance + 1);
        // Paper end - rewrite chunk system
    }

    public int getPlayerViewDistance(ServerPlayer player) { // Paper - public
        return ca.spottedleaf.moonrise.common.util.ChunkSystem.getSendViewDistance(player); // Paper - rewrite chunk system
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system

    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private static void dropChunk(ServerPlayer player, ChunkPos pos) {
        // Paper - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    @Override
    public CompletableFuture<Optional<CompoundTag>> read(final ChunkPos pos) {
        if (!ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.isRegionFileThread()) {
            try {
                return CompletableFuture.completedFuture(
                        Optional.ofNullable(
                            ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.loadData(
                                        this.level, pos.x, pos.z, ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.RegionFileType.CHUNK_DATA,
                                         ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
                                )
                        )
                );
            } catch (final Throwable thr) {
                return CompletableFuture.failedFuture(thr);
            }
        }
        return super.read(pos);
    }

    @Override
    public CompletableFuture<Void> write(final ChunkPos pos, final CompoundTag tag) {
        if (!ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.isRegionFileThread()) {
            ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.scheduleSave(
                this.level, pos.x, pos.z, tag,
                ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.RegionFileType.CHUNK_DATA);
            return null;
        }
        super.write(pos, tag);
        return null;
    }

    @Override
    public void flushWorker() {
        ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.flush();
    }
    // Paper end - rewrite chunk system

    @Nullable
    public LevelChunk getChunkToSend(long pos) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos);

        return playerchunk == null ? null : playerchunk.getChunkToSend();
    }

    public int size() {
        return ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolderCount(this.level); // Paper
    }

    public DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<ChunkHolder> getChunks() {
        return Iterables.unmodifiableIterable(ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolders(this.level)); // Paper
    }

    void dumpChunks(Writer writer) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("z").addColumn("level").addColumn("in_memory").addColumn("status").addColumn("full_status").addColumn("accessible_ready").addColumn("ticking_ready").addColumn("entity_ticking_ready").addColumn("ticket").addColumn("spawning").addColumn("block_entity_count").addColumn("ticking_ticket").addColumn("ticking_level").addColumn("block_ticks").addColumn("fluid_ticks").build(writer);
        TickingTracker tickingtracker = this.distanceManager.tickingTracker();
        Iterator<ChunkHolder> objectbidirectionaliterator = ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolders(this.level).iterator(); // Paper

        while (objectbidirectionaliterator.hasNext()) {
            ChunkHolder playerchunk = objectbidirectionaliterator.next(); // Paper
            long i = playerchunk.pos.toLong(); // Paper
            ChunkPos chunkcoordintpair = new ChunkPos(i);
            // Paper - move up
            Optional<ChunkAccess> optional = Optional.ofNullable(playerchunk.getLatestChunk());
            Optional<LevelChunk> optional1 = optional.flatMap((ichunkaccess) -> {
                return ichunkaccess instanceof LevelChunk ? Optional.of((LevelChunk) ichunkaccess) : Optional.empty();
            });

            // CraftBukkit - decompile error
            csvwriter.writeRow(chunkcoordintpair.x, chunkcoordintpair.z, playerchunk.getTicketLevel(), optional.isPresent(), optional.map(ChunkAccess::getPersistedStatus).orElse(null), optional1.map(LevelChunk::getFullStatus).orElse(null), ChunkMap.printFuture(playerchunk.getFullChunkFuture()), ChunkMap.printFuture(playerchunk.getTickingChunkFuture()), ChunkMap.printFuture(playerchunk.getEntityTickingChunkFuture()), this.distanceManager.getTicketDebugString(i), this.anyPlayerCloseEnoughForSpawning(chunkcoordintpair), optional1.map((chunk) -> {
                return chunk.getBlockEntities().size();
            }).orElse(0), tickingtracker.getTicketDebugString(i), tickingtracker.getLevel(i), optional1.map((chunk) -> {
                return chunk.getBlockTicks().count();
            }).orElse(0), optional1.map((chunk) -> {
                return chunk.getFluidTicks().count();
            }).orElse(0));
        }

    }

    private static String printFuture(CompletableFuture<ChunkResult<LevelChunk>> future) {
        try {
            ChunkResult<LevelChunk> chunkresult = (ChunkResult) future.getNow(null); // CraftBukkit - decompile error

            return chunkresult != null ? (chunkresult.isSuccess() ? "done" : "unloaded") : "not completed";
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos chunkPos) {
        return this.read(chunkPos).thenApplyAsync((optional) -> {
            return optional.map((nbttagcompound) -> this.upgradeChunkTag(nbttagcompound, chunkPos)); // CraftBukkit
        }, Util.backgroundExecutor());
    }

    // CraftBukkit start
    public CompoundTag upgradeChunkTag(CompoundTag nbttagcompound, ChunkPos chunkcoordintpair) { // Paper - public
        return this.upgradeChunkTag(this.level.getTypeKey(), this.overworldDataStorage, nbttagcompound, this.generator().getTypeNameForDataFixer(), chunkcoordintpair, this.level);
        // CraftBukkit end
    }

    public boolean anyPlayerCloseEnoughForSpawning(ChunkPos pos) { // Paper - public
        // Spigot start
        return this.anyPlayerCloseEnoughForSpawning(pos, false);
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkcoordintpair, boolean reducedRange) {
        int chunkRange = this.level.spigotConfig.mobSpawnRange;
        chunkRange = (chunkRange > this.level.spigotConfig.viewDistance) ? (byte) this.level.spigotConfig.viewDistance : chunkRange;
        chunkRange = (chunkRange > 8) ? 8 : chunkRange;

        final int finalChunkRange = chunkRange; // Paper for lambda below
        //double blockRange = (reducedRange) ? Math.pow(chunkRange << 4, 2) : 16384.0D; // Paper - use from event
        double blockRange = 16384.0D; // Paper
        // Spigot end
        if (!this.distanceManager.hasPlayersNearby(chunkcoordintpair.toLong())) {
            return false;
        } else {
            Iterator iterator = this.playerMap.getAllPlayers().iterator();

            ServerPlayer entityplayer;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                entityplayer = (ServerPlayer) iterator.next();
                // Paper start - PlayerNaturallySpawnCreaturesEvent
                com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent event;
                blockRange = 16384.0D;
                if (reducedRange) {
                    event = entityplayer.playerNaturallySpawnedEvent;
                    if (event == null || event.isCancelled()) return false;
                    blockRange = (double) ((event.getSpawnRadius() << 4) * (event.getSpawnRadius() << 4));
                }
                // Paper end - PlayerNaturallySpawnCreaturesEvent
            } while (!this.playerIsCloseEnoughForSpawning(entityplayer, chunkcoordintpair, blockRange)); // Spigot

            return true;
        }
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos pos) {
        long i = pos.toLong();

        if (!this.distanceManager.hasPlayersNearby(i)) {
            return List.of();
        } else {
            Builder<ServerPlayer> builder = ImmutableList.builder();
            Iterator iterator = this.playerMap.getAllPlayers().iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (this.playerIsCloseEnoughForSpawning(entityplayer, pos, 16384.0D)) { // Spigot
                    builder.add(entityplayer);
                }
            }

            return builder.build();
        }
    }

    private boolean playerIsCloseEnoughForSpawning(ServerPlayer entityplayer, ChunkPos chunkcoordintpair, double range) { // Spigot
        if (entityplayer.isSpectator()) {
            return false;
        } else {
            double d0 = ChunkMap.euclideanDistanceSquared(chunkcoordintpair, entityplayer);

            return d0 < range; // Spigot
        }
    }

    private boolean skipPlayer(ServerPlayer player) {
        return player.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(ServerPlayer player, boolean added) {
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = this.playerMap.ignoredOrUnknown(player);

        if (added) {
            this.playerMap.addPlayer(player, flag1);
            this.updatePlayerPos(player);
            if (!flag1) {
                this.distanceManager.addPlayer(SectionPos.of((EntityAccess) player), player);
                ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager)this.distanceManager).moonrise$addPlayer(player, SectionPos.of(player)); // Paper - chunk tick iteration optimisation
            }

            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            ca.spottedleaf.moonrise.common.util.ChunkSystem.addPlayerToDistanceMaps(this.level, player); // Paper - rewrite chunk system
        } else {
            SectionPos sectionposition = player.getLastSectionPos();

            this.playerMap.removePlayer(player);
            if (!flag2) {
                this.distanceManager.removePlayer(sectionposition, player);
                ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager)this.distanceManager).moonrise$removePlayer(player, SectionPos.of(player)); // Paper - chunk tick iteration optimisation
            }

            ca.spottedleaf.moonrise.common.util.ChunkSystem.removePlayerFromDistanceMaps(this.level, player); // Paper - rewrite chunk system
        }

    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionposition = SectionPos.of((EntityAccess) player);

        player.setLastSectionPos(sectionposition);
    }

    public void move(ServerPlayer player) {
        // Paper - optimise entity tracker

        SectionPos sectionposition = player.getLastSectionPos();
        SectionPos sectionposition1 = SectionPos.of((EntityAccess) player);
        boolean flag = this.playerMap.ignored(player);
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = sectionposition.asLong() != sectionposition1.asLong();

        if (flag2 || flag != flag1) {
            this.updatePlayerPos(player);
            ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager)this.distanceManager).moonrise$updatePlayer(player, sectionposition, sectionposition1, flag, flag1); // Paper - chunk tick iteration optimisation
            if (!flag) {
                this.distanceManager.removePlayer(sectionposition, player);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionposition1, player);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(player);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(player);
            }

            // Paper - rewrite chunk system
        }

        ca.spottedleaf.moonrise.common.util.ChunkSystem.updateMaps(this.level, player); // Paper - rewrite chunk system
    }

    private void updateChunkTracking(ServerPlayer player) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkFilter) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge) {
        // Paper start - rewrite chunk system
        final ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos.toLong());
        if (holder == null) {
            return new ArrayList<>();
        } else {
            return ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)holder).moonrise$getPlayers(onlyOnWatchDistanceEdge);
        }
        // Paper end - rewrite chunk system
    }

    public void addEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity track"); // Spigot
        // Paper start - ignore and warn about illegal addEntity calls instead of crashing server
        if (!entity.valid || entity.level() != this.level || this.entityMap.containsKey(entity.getId())) {
            LOGGER.error("Illegal ChunkMap::addEntity for world " + this.level.getWorld().getName()
                + ": " + entity  + (this.entityMap.containsKey(entity.getId()) ? " ALREADY CONTAINED (This would have crashed your server)" : ""), new Throwable());
            return;
        }
        // Paper end - ignore and warn about illegal addEntity calls instead of crashing server
        if (entity instanceof ServerPlayer && ((ServerPlayer) entity).supressTrackerForLogin) return; // Paper - Fire PlayerJoinEvent when Player is actually ready; Delay adding to tracker until after list packets
        if (!(entity instanceof EnderDragonPart)) {
            EntityType<?> entitytypes = entity.getType();
            int i = entitytypes.clientTrackingRange() * 16;
            i = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, i); // Spigot

            if (i != 0) {
                int j = entitytypes.updateInterval();

                if (this.entityMap.containsKey(entity.getId())) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    ChunkMap.TrackedEntity playerchunkmap_entitytracker = new ChunkMap.TrackedEntity(entity, i, j, entitytypes.trackDeltas());

                    this.entityMap.put(entity.getId(), playerchunkmap_entitytracker);
                    // Paper start - optimise entity tracker
                    if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity() != null) {
                        throw new IllegalStateException("Entity is already tracked");
                    }
                    ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(playerchunkmap_entitytracker);
                    // Paper end - optimise entity tracker
                    playerchunkmap_entitytracker.updatePlayers(this.level.players());
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer entityplayer = (ServerPlayer) entity;

                        this.updatePlayerStatus(entityplayer, true);
                        ObjectIterator objectiterator = this.entityMap.values().iterator();

                        while (objectiterator.hasNext()) {
                            ChunkMap.TrackedEntity playerchunkmap_entitytracker1 = (ChunkMap.TrackedEntity) objectiterator.next();

                            if (playerchunkmap_entitytracker1.entity != entityplayer) {
                                playerchunkmap_entitytracker1.updatePlayer(entityplayer);
                            }
                        }
                    }

                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity untrack"); // Spigot
        if (entity instanceof ServerPlayer entityplayer) {
            this.updatePlayerStatus(entityplayer, false);
            ObjectIterator objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();

                playerchunkmap_entitytracker.removePlayer(entityplayer);
            }
        }

        ChunkMap.TrackedEntity playerchunkmap_entitytracker1 = (ChunkMap.TrackedEntity) this.entityMap.remove(entity.getId());

        if (playerchunkmap_entitytracker1 != null) {
            playerchunkmap_entitytracker1.broadcastRemoved();
        }

        ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(null); // Paper - optimise entity tracker
    }

    // Paper start - optimise entity tracker
    private void newTrackerTick() {
        final ca.spottedleaf.moonrise.common.misc.NearbyPlayers nearbyPlayers = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getNearbyPlayers();
        final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup entityLookup = (ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup)((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getEntityLookup();;

        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
            final Entity entity = trackerEntitiesRaw[i];
            final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(nearbyPlayers.getChunk(entity.chunkPosition()));
            tracker.serverEntity.sendChanges();
        }

        // process unloads
        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> unloadedEntities = entityLookup.trackerUnloadedEntities;
        final Entity[] unloadedEntitiesRaw = java.util.Arrays.copyOf(unloadedEntities.getRawDataUnchecked(), unloadedEntities.size());
        unloadedEntities.clear();

        for (final Entity entity : unloadedEntitiesRaw) {
            final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$clearPlayers();
        }
    }
    // Paper end - optimise entity tracker

    protected void tick() {
        // Paper start - optimise entity tracker
        if (true) {
            this.newTrackerTick();
            return;
        }
        // Paper end - optimise entity tracker
        // Paper - rewrite chunk system

        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list1 = this.level.players();
        ObjectIterator objectiterator = this.entityMap.values().iterator();
        level.timings.tracker1.startTiming(); // Paper

        ChunkMap.TrackedEntity playerchunkmap_entitytracker;

        while (objectiterator.hasNext()) {
            playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
            SectionPos sectionposition = playerchunkmap_entitytracker.lastSectionPos;
            SectionPos sectionposition1 = SectionPos.of((EntityAccess) playerchunkmap_entitytracker.entity);
            boolean flag = !Objects.equals(sectionposition, sectionposition1);

            if (flag) {
                playerchunkmap_entitytracker.updatePlayers(list1);
                Entity entity = playerchunkmap_entitytracker.entity;

                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer) entity);
                }

                playerchunkmap_entitytracker.lastSectionPos = sectionposition1;
            }

            if (flag || this.distanceManager.inEntityTickingRange(sectionposition1.chunk().toLong())) {
                playerchunkmap_entitytracker.serverEntity.sendChanges();
            }
        }
        level.timings.tracker1.stopTiming(); // Paper

        if (!list.isEmpty()) {
            objectiterator = this.entityMap.values().iterator();

            level.timings.tracker2.startTiming(); // Paper
            while (objectiterator.hasNext()) {
                playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
                playerchunkmap_entitytracker.updatePlayers(list);
            }
            level.timings.tracker2.stopTiming(); // Paper
        }

    }

    public void broadcast(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcast(packet);
        }

    }

    protected void broadcastAndSend(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcastAndSend(packet);
        }

    }

    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap();
        Iterator iterator = chunks.iterator();

        while (iterator.hasNext()) {
            ChunkAccess ichunkaccess = (ChunkAccess) iterator.next();
            ChunkPos chunkcoordintpair = ichunkaccess.getPos();
            LevelChunk chunk;

            if (ichunkaccess instanceof LevelChunk chunk1) {
                chunk = chunk1;
            } else {
                chunk = this.level.getChunk(chunkcoordintpair.x, chunkcoordintpair.z);
            }

            Iterator iterator1 = this.getPlayers(chunkcoordintpair, false).iterator();

            while (iterator1.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

                ((List) map.computeIfAbsent(entityplayer, (entityplayer1) -> {
                    return new ArrayList();
                })).add(chunk);
            }
        }

        map.forEach((entityplayer1, list1) -> {
            entityplayer1.connection.send(ClientboundChunksBiomesPacket.forChunks(list1));
        });
    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus levelType) {
        this.chunkStatusListener.onChunkStatusChange(chunkPos, levelType);
    }

    public void waitForLightBeforeSending(ChunkPos centerPos, int radius) {
        // Paper - rewrite chunk system
    }

    public class ChunkDistanceManager extends DistanceManager implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager { // Paper - public // Paper - rewrite chunk system

        protected ChunkDistanceManager(final Executor workerExecutor, final Executor mainThreadExecutor) {
            super(workerExecutor, mainThreadExecutor);
        }

        // Paper start - rewrite chunk system
        @Override
        public final ChunkMap moonrise$getChunkMap() {
            return ChunkMap.this;
        }
        // Paper end - rewrite chunk system

        @Override
        protected boolean isChunkToRemove(long pos) {
            throw new UnsupportedOperationException(); // Paper - rewrite chunk system
        }

        @Nullable
        @Override
        protected ChunkHolder getChunk(long pos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(pos);
        }

        @Nullable
        @Override
        protected ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k) {
            return ChunkMap.this.updateChunkScheduling(pos, level, holder, k);
        }
    }

    public class TrackedEntity implements ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity { // Paper - optimise entity tracker

        public final ServerEntity serverEntity;
        final Entity entity;
        private final int range;
        SectionPos lastSectionPos;
        public final Set<ServerPlayerConnection> seenBy = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(); // Paper - Perf: optimise map impl

        // Paper start - optimise entity tracker
        private long lastChunkUpdate = -1L;
        private ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk lastTrackedChunk;

        @Override
        public final void moonrise$tick(final ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk chunk) {
            if (chunk == null) {
                this.moonrise$clearPlayers();
                return;
            }

            final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> players = chunk.getPlayers(ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.VIEW_DISTANCE);

            if (players == null) {
                this.moonrise$clearPlayers();
                return;
            }

            final long lastChunkUpdate = this.lastChunkUpdate;
            final long currChunkUpdate = chunk.getUpdateCount();
            final ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk lastTrackedChunk = this.lastTrackedChunk;
            this.lastChunkUpdate = currChunkUpdate;
            this.lastTrackedChunk = chunk;

            final ServerPlayer[] playersRaw = players.getRawDataUnchecked();

            for (int i = 0, len = players.size(); i < len; ++i) {
                final ServerPlayer player = playersRaw[i];
                this.updatePlayer(player);
            }

            if (lastChunkUpdate != currChunkUpdate || lastTrackedChunk != chunk) {
                // need to purge any players possible not in the chunk list
                for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                    final ServerPlayer player = conn.getPlayer();
                    if (!players.contains(player)) {
                        this.removePlayer(player);
                    }
                }
            }
        }

        @Override
        public final void moonrise$removeNonTickThreadPlayers() {
            boolean foundToRemove = false;
            for (final ServerPlayerConnection conn : this.seenBy) {
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(conn.getPlayer())) {
                    foundToRemove = true;
                    break;
                }
            }

            if (!foundToRemove) {
                return;
            }

            for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                ServerPlayer player = conn.getPlayer();
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player)) {
                    this.removePlayer(player);
                }
            }
        }

        @Override
        public final void moonrise$clearPlayers() {
            this.lastChunkUpdate = -1;
            this.lastTrackedChunk = null;
            if (this.seenBy.isEmpty()) {
                return;
            }
            for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                ServerPlayer player = conn.getPlayer();
                this.removePlayer(player);
            }
        }
        // Paper end - optimise entity tracker

        public TrackedEntity(final Entity entity, final int i, final int j, final boolean flag) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, j, flag, this::broadcast, this.seenBy); // CraftBukkit
            this.entity = entity;
            this.range = i;
            this.lastSectionPos = SectionPos.of((EntityAccess) entity);
        }

        public boolean equals(Object object) {
            return object instanceof ChunkMap.TrackedEntity ? ((ChunkMap.TrackedEntity) object).entity.getId() == this.entity.getId() : false;
        }

        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> packet) {
            Iterator iterator = this.seenBy.iterator();

            while (iterator.hasNext()) {
                ServerPlayerConnection serverplayerconnection = (ServerPlayerConnection) iterator.next();

                serverplayerconnection.send(packet);
            }

        }

        public void broadcastAndSend(Packet<?> packet) {
            this.broadcast(packet);
            if (this.entity instanceof ServerPlayer) {
                ((ServerPlayer) this.entity).connection.send(packet);
            }

        }

        public void broadcastRemoved() {
            Iterator iterator = this.seenBy.iterator();

            while (iterator.hasNext()) {
                ServerPlayerConnection serverplayerconnection = (ServerPlayerConnection) iterator.next();

                this.serverEntity.removePairing(serverplayerconnection.getPlayer());
            }

        }

        public void removePlayer(ServerPlayer player) {
            org.spigotmc.AsyncCatcher.catchOp("player tracker clear"); // Spigot
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
            }

        }

        public void updatePlayer(ServerPlayer player) {
            org.spigotmc.AsyncCatcher.catchOp("player tracker update"); // Spigot
            if (player != this.entity) {
                // Paper start - remove allocation of Vec3D here
                // Vec3 vec3d = player.position().subtract(this.entity.position());
                double vec3d_dx = player.getX() - this.entity.getX();
                double vec3d_dz = player.getZ() - this.entity.getZ();
                // Paper end - remove allocation of Vec3D here
                int i = ChunkMap.this.getPlayerViewDistance(player);
                double d0 = (double) Math.min(this.getEffectiveRange(), i * 16);
                double d1 = vec3d_dx * vec3d_dx + vec3d_dz * vec3d_dz; // Paper
                double d2 = d0 * d0;
                // Paper start - Configurable entity tracking range by Y
                boolean flag = d1 <= d2;
                if (flag && level.paperConfig().entities.trackingRangeY.enabled) {
                    double rangeY = level.paperConfig().entities.trackingRangeY.get(this.entity, -1);
                    if (rangeY != -1) {
                        double vec3d_dy = player.getY() - this.entity.getY();
                        flag = vec3d_dy * vec3d_dy <= rangeY * rangeY;
                    }
                }
                flag = flag && this.entity.broadcastToPlayer(player) && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition().x, this.entity.chunkPosition().z);
                // Paper end - Configurable entity tracking range by Y

                // CraftBukkit start - respect vanish API
                if (flag && !player.getBukkitEntity().canSee(this.entity.getBukkitEntity())) { // Paper - only consider hits
                    flag = false;
                }
                // CraftBukkit end
                if (flag) {
                    if (this.seenBy.add(player.connection)) {
                        // Paper start - entity tracking events
                        if (io.papermc.paper.event.player.PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length == 0 || new io.papermc.paper.event.player.PlayerTrackEntityEvent(player.getBukkitEntity(), this.entity.getBukkitEntity()).callEvent()) {
                        this.serverEntity.addPairing(player);
                        }
                        // Paper end - entity tracking events
                    }
                } else if (this.seenBy.remove(player.connection)) {
                    this.serverEntity.removePairing(player);
                }

            }
        }

        private int scaledRange(int initialDistance) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(initialDistance);
        }

        private int getEffectiveRange() {
            int i = this.range;
            Iterator iterator = this.entity.getIndirectPassengers().iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();
                int j = entity.getType().clientTrackingRange() * 16;
                j = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, j); // Paper

                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> players) {
            Iterator iterator = players.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                this.updatePlayer(entityplayer);
            }

        }
    }
}
