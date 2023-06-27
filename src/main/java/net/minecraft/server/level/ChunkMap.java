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
    public final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap = new Long2ObjectLinkedOpenHashMap();
    public volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    private final List<ChunkGenerationTask> pendingGenerationTasks;
    public final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final Supplier<DimensionDataStorage> overworldDataStorage;
    private final PoiManager poiManager;
    public final LongSet toDrop;
    private boolean modified;
    private final ChunkTaskPriorityQueueSorter queueSorter;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> mainThreadMailbox;
    public final ChunkProgressListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    public final ChunkMap.ChunkDistanceManager distanceManager;
    private final AtomicInteger tickingGenerated;
    private final String storageName;
    private final PlayerMap playerMap;
    public final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;
    private final Long2ByteMap chunkTypeCache;
    private final Long2LongMap chunkSaveCooldowns;
    private final Queue<Runnable> unloadQueue;
    public int serverViewDistance;
    private final WorldGenContext worldGenContext;

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
        return this.pendingUnloads.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }
    // Paper end

    public ChunkMap(ServerLevel world, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor executor, BlockableEventLoop<Runnable> mainThreadExecutor, LightChunkGetter chunkProvider, ChunkGenerator chunkGenerator, ChunkProgressListener worldGenerationProgressListener, ChunkStatusUpdateListener chunkStatusChangeListener, Supplier<DimensionDataStorage> persistentStateManagerFactory, int viewDistance, boolean dsync) {
        super(new RegionStorageInfo(session.getLevelId(), world.dimension(), "chunk"), session.getDimensionPath(world.dimension()).resolve("region"), dataFixer, dsync);
        this.visibleChunkMap = this.updatingChunkMap.clone();
        this.pendingUnloads = new Long2ObjectLinkedOpenHashMap();
        this.pendingGenerationTasks = new ArrayList();
        this.toDrop = new LongOpenHashSet();
        this.tickingGenerated = new AtomicInteger();
        this.playerMap = new PlayerMap();
        this.entityMap = new Int2ObjectOpenHashMap();
        this.chunkTypeCache = new Long2ByteOpenHashMap();
        this.chunkSaveCooldowns = new Long2LongOpenHashMap();
        this.unloadQueue = Queues.newConcurrentLinkedQueue();
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

        this.queueSorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(threadedmailbox, mailbox, threadedmailbox1), executor, Integer.MAX_VALUE);
        this.worldgenMailbox = this.queueSorter.getProcessor(threadedmailbox, false);
        this.mainThreadMailbox = this.queueSorter.getProcessor(mailbox, false);
        this.lightEngine = new ThreadedLevelLightEngine(chunkProvider, this, this.level.dimensionType().hasSkyLight(), threadedmailbox1, this.queueSorter.getProcessor(threadedmailbox1, false));
        this.distanceManager = new ChunkMap.ChunkDistanceManager(executor, mainThreadExecutor);
        this.overworldDataStorage = persistentStateManagerFactory;
        this.poiManager = new PoiManager(new RegionStorageInfo(session.getLevelId(), world.dimension(), "poi"), path.resolve("poi"), dataFixer, dsync, iregistrycustom, world.getServer(), world);
        this.setServerViewDistance(viewDistance);
        this.worldGenContext = new WorldGenContext(world, chunkGenerator, structureTemplateManager, this.lightEngine, this.mainThreadMailbox);
    }

    // Paper start
    public int getMobCountNear(final ServerPlayer player, final net.minecraft.world.entity.MobCategory mobCategory) {
        return -1;
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
        return player.getChunkTrackingView().contains(chunkX, chunkZ) && !player.connection.chunkSender.isPending(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean isChunkOnTrackedBorder(ServerPlayer player, int chunkX, int chunkZ) {
        if (!this.isChunkTracked(player, chunkX, chunkZ)) {
            return false;
        } else {
            for (int k = -1; k <= 1; ++k) {
                for (int l = -1; l <= 1; ++l) {
                    if ((k != 0 || l != 0) && !this.isChunkTracked(player, chunkX + k, chunkZ + l)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    protected ChunkHolder getUpdatingChunkIfPresent(long pos) {
        return (ChunkHolder) this.updatingChunkMap.get(pos);
    }

    @Nullable
    public ChunkHolder getVisibleChunkIfPresent(long pos) {
        return (ChunkHolder) this.visibleChunkMap.get(pos);
    }

    protected IntSupplier getChunkQueueLevel(long pos) {
        return () -> {
            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos);

            return playerchunk == null ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1 : Math.min(playerchunk.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
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
        if (margin == 0) {
            ChunkStatus chunkstatus = (ChunkStatus) distanceToStatus.apply(0);

            return centerChunk.scheduleChunkGenerationTask(chunkstatus, this).thenApply((chunkresult) -> {
                return chunkresult.map(List::of);
            });
        } else {
            List<CompletableFuture<ChunkResult<ChunkAccess>>> list = new ArrayList();
            ChunkPos chunkcoordintpair = centerChunk.getPos();

            for (int j = -margin; j <= margin; ++j) {
                for (int k = -margin; k <= margin; ++k) {
                    int l = Math.max(Math.abs(k), Math.abs(j));
                    long i1 = ChunkPos.asLong(chunkcoordintpair.x + k, chunkcoordintpair.z + j);
                    ChunkHolder playerchunk1 = this.getUpdatingChunkIfPresent(i1);

                    if (playerchunk1 == null) {
                        return ChunkMap.UNLOADED_CHUNK_LIST_FUTURE;
                    }

                    ChunkStatus chunkstatus1 = (ChunkStatus) distanceToStatus.apply(l);

                    list.add(playerchunk1.scheduleChunkGenerationTask(chunkstatus1, this));
                }
            }

            return Util.sequence(list).thenApply((list1) -> {
                List<ChunkAccess> list2 = Lists.newArrayList();
                Iterator iterator = list1.iterator();

                while (iterator.hasNext()) {
                    ChunkResult<ChunkAccess> chunkresult = (ChunkResult) iterator.next();

                    if (chunkresult == null) {
                        throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                    }

                    ChunkAccess ichunkaccess = (ChunkAccess) chunkresult.orElse(null); // CraftBukkit - decompile error

                    if (ichunkaccess == null) {
                        return ChunkMap.UNLOADED_CHUNK_LIST_RESULT;
                    }

                    list2.add(ichunkaccess);
                }

                return ChunkResult.of(list2);
            });
        }
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
        return this.getChunkRangeFuture(holder, 2, (i) -> {
            return ChunkStatus.FULL;
        }).thenApplyAsync((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (LevelChunk) list.get(list.size() / 2);
            });
        }, this.mainThreadExecutor);
    }

    @Nullable
    ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k) {
        if (!ChunkLevel.isLoaded(k) && !ChunkLevel.isLoaded(level)) {
            return holder;
        } else {
            if (holder != null) {
                holder.setTicketLevel(level);
            }

            if (holder != null) {
                if (!ChunkLevel.isLoaded(level)) {
                    this.toDrop.add(pos);
                } else {
                    this.toDrop.remove(pos);
                }
            }

            if (ChunkLevel.isLoaded(level) && holder == null) {
                holder = (ChunkHolder) this.pendingUnloads.remove(pos);
                if (holder != null) {
                    holder.setTicketLevel(level);
                } else {
                    holder = new ChunkHolder(new ChunkPos(pos), level, this.level, this.lightEngine, this.queueSorter, this);
                    // Paper start
                    ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkHolderCreate(this.level, holder);
                    // Paper end
                }

                this.updatingChunkMap.put(pos, holder);
                this.modified = true;
            }

            return holder;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.queueSorter.close();
            this.poiManager.close();
        } finally {
            super.close();
        }

    }

    protected void saveAllChunks(boolean flush) {
        if (flush) {
            List<ChunkHolder> list = ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolders(this.level).stream().filter(ChunkHolder::wasAccessibleSinceLastSave).peek(ChunkHolder::refreshAccessibility).toList(); // Paper
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream().map((playerchunk) -> {
                    BlockableEventLoop iasynctaskhandler = this.mainThreadExecutor;

                    Objects.requireNonNull(playerchunk);
                    iasynctaskhandler.managedBlock(playerchunk::isReadyForSaving);
                    return playerchunk.getLatestChunk();
                }).filter((ichunkaccess) -> {
                    return ichunkaccess instanceof ImposterProtoChunk || ichunkaccess instanceof LevelChunk;
                }).filter(this::save).forEach((ichunkaccess) -> {
                    mutableboolean.setTrue();
                });
            } while (mutableboolean.isTrue());

            this.processUnloads(() -> {
                return true;
            });
            this.flushWorker();
        } else {
            ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolders(this.level).forEach(this::saveChunkIfNeeded);
        }

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
        return this.lightEngine.hasLightWork() || !this.pendingUnloads.isEmpty() || ca.spottedleaf.moonrise.common.util.ChunkSystem.hasAnyChunkHolders(this.level) || this.poiManager.hasWork() || !this.toDrop.isEmpty() || !this.unloadQueue.isEmpty() || this.queueSorter.hasWork() || this.distanceManager.hasTickets(); // Paper
    }

    private void processUnloads(BooleanSupplier shouldKeepTicking) {
        LongIterator longiterator = this.toDrop.iterator();
        int i = 0;

        while (longiterator.hasNext() && (shouldKeepTicking.getAsBoolean() || i < 200 || this.toDrop.size() > 2000)) {
            long j = longiterator.nextLong();
            ChunkHolder playerchunk = (ChunkHolder) this.updatingChunkMap.get(j);

            if (playerchunk != null) {
                if (playerchunk.getGenerationRefCount() != 0) {
                    continue;
                }

                this.updatingChunkMap.remove(j);
                this.pendingUnloads.put(j, playerchunk);
                this.modified = true;
                ++i;
                this.scheduleUnload(j, playerchunk);
            }

            longiterator.remove();
        }

        int k = Math.max(0, this.unloadQueue.size() - 2000);

        Runnable runnable;

        while ((shouldKeepTicking.getAsBoolean() || k > 0) && (runnable = (Runnable) this.unloadQueue.poll()) != null) {
            --k;
            runnable.run();
        }

        int l = 0;
        Iterator<ChunkHolder> objectiterator = ca.spottedleaf.moonrise.common.util.ChunkSystem.getVisibleChunkHolders(this.level).iterator(); // Paper

        while (l < 20 && shouldKeepTicking.getAsBoolean() && objectiterator.hasNext()) {
            if (this.saveChunkIfNeeded((ChunkHolder) objectiterator.next())) {
                ++l;
            }
        }

    }

    private void scheduleUnload(long pos, ChunkHolder holder) {
        CompletableFuture completablefuture = holder.getSaveSyncFuture();
        Runnable runnable = () -> {
            if (!holder.isReadyForSaving()) {
                this.scheduleUnload(pos, holder);
            } else {
                ChunkAccess ichunkaccess = holder.getLatestChunk();

                // Paper start
                boolean removed;
                if ((removed = this.pendingUnloads.remove(pos, holder)) && ichunkaccess != null) {
                    ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkHolderDelete(this.level, holder);
                    // Paper end
                    LevelChunk chunk;

                    if (ichunkaccess instanceof LevelChunk) {
                        chunk = (LevelChunk) ichunkaccess;
                        chunk.setLoaded(false);
                    }

                    this.save(ichunkaccess);
                    if (ichunkaccess instanceof LevelChunk) {
                        chunk = (LevelChunk) ichunkaccess;
                        this.level.unload(chunk);
                    }

                    this.lightEngine.updateChunkStatus(ichunkaccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.progressListener.onStatusChange(ichunkaccess.getPos(), (ChunkStatus) null);
                    this.chunkSaveCooldowns.remove(ichunkaccess.getPos().toLong());
                } else if (removed) { // Paper start
                    ca.spottedleaf.moonrise.common.util.ChunkSystem.onChunkHolderDelete(this.level, holder);
                } // Paper end

            }
        };
        Queue queue = this.unloadQueue;

        Objects.requireNonNull(this.unloadQueue);
        completablefuture.thenRunAsync(runnable, queue::add).whenComplete((ovoid, throwable) -> {
            if (throwable != null) {
                ChunkMap.LOGGER.error("Failed to save chunk {}", holder.getPos(), throwable);
            }

        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        } else {
            this.visibleChunkMap = this.updatingChunkMap.clone();
            this.modified = false;
            return true;
        }
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos pos) {
        return this.readChunk(pos).thenApply((optional) -> {
            return optional.filter((nbttagcompound) -> {
                boolean flag = ChunkMap.isChunkDataValid(nbttagcompound);

                if (!flag) {
                    ChunkMap.LOGGER.error("Chunk file at {} is missing level data, skipping", pos);
                }

                return flag;
            });
        }).thenApplyAsync((optional) -> {
            this.level.getProfiler().incrementCounter("chunkLoad");
            if (optional.isPresent()) {
                ProtoChunk protochunk = ChunkSerializer.read(this.level, this.poiManager, this.storageInfo(), pos, (CompoundTag) optional.get());

                this.markPosition(pos, protochunk.getPersistedStatus().getChunkType());
                return protochunk;
            } else {
                return this.createEmptyChunk(pos);
            }
        }, this.mainThreadExecutor).exceptionallyAsync((throwable) -> {
            return this.handleChunkLoadFailure(throwable, pos);
        }, this.mainThreadExecutor);
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
        ChunkHolder playerchunk = (ChunkHolder) this.updatingChunkMap.get(pos);

        playerchunk.increaseGenerationRefCount();
        return playerchunk;
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder chunkHolder) {
        chunkHolder.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunkHolder, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks) {
        ChunkPos chunkcoordintpair = chunkHolder.getPos();

        if (step.targetStatus() == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkcoordintpair);
        } else {
            try {
                GenerationChunkHolder generationchunkholder1 = (GenerationChunkHolder) chunks.get(chunkcoordintpair.x, chunkcoordintpair.z);
                ChunkAccess ichunkaccess = generationchunkholder1.getChunkIfPresentUnchecked(step.targetStatus().getParent());

                if (ichunkaccess == null) {
                    throw new IllegalStateException("Parent chunk missing");
                } else {
                    CompletableFuture<ChunkAccess> completablefuture = step.apply(this.worldGenContext, chunks, ichunkaccess);

                    this.progressListener.onStatusChange(chunkcoordintpair, step.targetStatus());
                    return completablefuture;
                }
            } catch (Exception exception) {
                exception.getStackTrace();
                CrashReport crashreport = CrashReport.forThrowable(exception, "Exception generating new chunk");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Chunk to be generated");

                crashreportsystemdetails.setDetail("Status being generated", () -> {
                    return step.targetStatus().getName();
                });
                crashreportsystemdetails.setDetail("Location", (Object) String.format(Locale.ROOT, "%d,%d", chunkcoordintpair.x, chunkcoordintpair.z));
                crashreportsystemdetails.setDetail("Position hash", (Object) ChunkPos.asLong(chunkcoordintpair.x, chunkcoordintpair.z));
                crashreportsystemdetails.setDetail("Generator", (Object) this.generator());
                this.mainThreadExecutor.execute(() -> {
                    throw new ReportedException(crashreport);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus requestedStatus, ChunkPos pos) {
        ChunkGenerationTask chunkgenerationtask = ChunkGenerationTask.create(this, requestedStatus, pos);

        this.pendingGenerationTasks.add(chunkgenerationtask);
        return chunkgenerationtask;
    }

    private void runGenerationTask(ChunkGenerationTask chunkLoader) {
        this.worldgenMailbox.tell(ChunkTaskPriorityQueueSorter.message(chunkLoader.getCenter(), () -> {
            CompletableFuture<?> completablefuture = chunkLoader.runUntilWait();

            if (completablefuture != null) {
                completablefuture.thenRun(() -> {
                    this.runGenerationTask(chunkLoader);
                });
            }
        }));
    }

    @Override
    public void runGenerationTasks() {
        this.pendingGenerationTasks.forEach(this::runGenerationTask);
        this.pendingGenerationTasks.clear();
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(ChunkHolder holder) {
        CompletableFuture<ChunkResult<List<ChunkAccess>>> completablefuture = this.getChunkRangeFuture(holder, 1, (i) -> {
            return ChunkStatus.FULL;
        });
        CompletableFuture<ChunkResult<LevelChunk>> completablefuture1 = completablefuture.thenApplyAsync((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (LevelChunk) list.get(list.size() / 2);
            });
        }, (runnable) -> {
            this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, runnable));
        }).thenApplyAsync((chunkresult) -> {
            return chunkresult.ifSuccess((chunk) -> {
                chunk.postProcessGeneration();
                this.level.startTickingChunk(chunk);
                CompletableFuture<?> completablefuture2 = holder.getSendSyncFuture();

                if (completablefuture2.isDone()) {
                    this.onChunkReadyToSend(chunk);
                } else {
                    completablefuture2.thenAcceptAsync((object) -> {
                        this.onChunkReadyToSend(chunk);
                    }, this.mainThreadExecutor);
                }

            });
        }, this.mainThreadExecutor);

        completablefuture1.handle((chunkresult, throwable) -> {
            this.tickingGenerated.getAndIncrement();
            return null;
        });
        return completablefuture1;
    }

    private void onChunkReadyToSend(LevelChunk chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        Iterator iterator = this.playerMap.getAllPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.getChunkTrackingView().contains(chunkcoordintpair)) {
                ChunkMap.markChunkPendingToSend(entityplayer, chunk);
            }
        }

    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(ChunkHolder holder) {
        return this.getChunkRangeFuture(holder, 1, ChunkLevel::getStatusAroundFullChunk).thenApplyAsync((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (LevelChunk) list.get(list.size() / 2);
            });
        }, (runnable) -> {
            this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, runnable));
        });
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(ChunkHolder chunkHolder) {
        if (chunkHolder.wasAccessibleSinceLastSave() && chunkHolder.isReadyForSaving()) {
            ChunkAccess ichunkaccess = chunkHolder.getLatestChunk();

            if (!(ichunkaccess instanceof ImposterProtoChunk) && !(ichunkaccess instanceof LevelChunk)) {
                return false;
            } else {
                long i = ichunkaccess.getPos().toLong();
                long j = this.chunkSaveCooldowns.getOrDefault(i, -1L);
                long k = System.currentTimeMillis();

                if (k < j) {
                    return false;
                } else {
                    boolean flag = this.save(ichunkaccess);

                    chunkHolder.refreshAccessibility();
                    if (flag) {
                        this.chunkSaveCooldowns.put(i, k + 10000L);
                    }

                    return flag;
                }
            }
        } else {
            return false;
        }
    }

    public boolean save(ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.isUnsaved()) {
            return false;
        } else {
            chunk.setUnsaved(false);
            ChunkPos chunkcoordintpair = chunk.getPos();

            try {
                ChunkStatus chunkstatus = chunk.getPersistedStatus();

                if (chunkstatus.getChunkType() != ChunkType.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkcoordintpair)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && chunk.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                this.level.getProfiler().incrementCounter("chunkSave");
                CompoundTag nbttagcompound = ChunkSerializer.write(this.level, chunk);

                this.write(chunkcoordintpair, nbttagcompound).exceptionally((throwable) -> {
                    this.level.getServer().reportChunkSaveFailure(throwable, this.storageInfo(), chunkcoordintpair);
                    return null;
                });
                this.markPosition(chunkcoordintpair, chunkstatus.getChunkType());
                return true;
            } catch (Exception exception) {
                this.level.getServer().reportChunkSaveFailure(exception, this.storageInfo(), chunkcoordintpair);
                return false;
            }
        }
    }

    private boolean isExistingChunkFull(ChunkPos pos) {
        byte b0 = this.chunkTypeCache.get(pos.toLong());

        if (b0 != 0) {
            return b0 == 1;
        } else {
            CompoundTag nbttagcompound;

            try {
                nbttagcompound = (CompoundTag) ((Optional) this.readChunk(pos).join()).orElse((Object) null);
                if (nbttagcompound == null) {
                    this.markPositionReplaceable(pos);
                    return false;
                }
            } catch (Exception exception) {
                ChunkMap.LOGGER.error("Failed to read chunk {}", pos, exception);
                this.markPositionReplaceable(pos);
                return false;
            }

            ChunkType chunktype = ChunkSerializer.getChunkTypeFromTag(nbttagcompound);

            return this.markPosition(pos, chunktype) == 1;
        }
    }

    public void setServerViewDistance(int watchDistance) { // Paper - public
        int j = Mth.clamp(watchDistance, 2, 32);

        if (j != this.serverViewDistance) {
            this.serverViewDistance = j;
            this.distanceManager.updatePlayerTickets(this.serverViewDistance);
            Iterator iterator = this.playerMap.getAllPlayers().iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                this.updateChunkTracking(entityplayer);
            }
        }

    }

    public int getPlayerViewDistance(ServerPlayer player) { // Paper - public
        return Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos pos) {
        LevelChunk chunk = this.getChunkToSend(pos.toLong());

        if (chunk != null) {
            ChunkMap.markChunkPendingToSend(player, chunk);
        }

    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        player.connection.chunkSender.markChunkPendingToSend(chunk);
    }

    private static void dropChunk(ServerPlayer player, ChunkPos pos) {
        player.connection.chunkSender.dropChunk(player, pos);
    }

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
    private CompoundTag upgradeChunkTag(CompoundTag nbttagcompound, ChunkPos chunkcoordintpair) {
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
            }

            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            this.updateChunkTracking(player);
        } else {
            SectionPos sectionposition = player.getLastSectionPos();

            this.playerMap.removePlayer(player);
            if (!flag2) {
                this.distanceManager.removePlayer(sectionposition, player);
            }

            this.applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
        }

    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionposition = SectionPos.of((EntityAccess) player);

        player.setLastSectionPos(sectionposition);
    }

    public void move(ServerPlayer player) {
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();

            if (playerchunkmap_entitytracker.entity == player) {
                playerchunkmap_entitytracker.updatePlayers(this.level.players());
            } else {
                playerchunkmap_entitytracker.updatePlayer(player);
            }
        }

        SectionPos sectionposition = player.getLastSectionPos();
        SectionPos sectionposition1 = SectionPos.of((EntityAccess) player);
        boolean flag = this.playerMap.ignored(player);
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = sectionposition.asLong() != sectionposition1.asLong();

        if (flag2 || flag != flag1) {
            this.updatePlayerPos(player);
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

            this.updateChunkTracking(player);
        }

    }

    private void updateChunkTracking(ServerPlayer player) {
        ChunkPos chunkcoordintpair = player.chunkPosition();
        int i = this.getPlayerViewDistance(player);
        ChunkTrackingView chunktrackingview = player.getChunkTrackingView();

        if (chunktrackingview instanceof ChunkTrackingView.Positioned chunktrackingview_a) {
            if (chunktrackingview_a.center().equals(chunkcoordintpair) && chunktrackingview_a.viewDistance() == i) {
                return;
            }
        }

        this.applyChunkTrackingView(player, ChunkTrackingView.of(chunkcoordintpair, i));
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkFilter) {
        if (player.level() == this.level) {
            ChunkTrackingView chunktrackingview1 = player.getChunkTrackingView();

            if (chunkFilter instanceof ChunkTrackingView.Positioned) {
                label15:
                {
                    ChunkTrackingView.Positioned chunktrackingview_a = (ChunkTrackingView.Positioned) chunkFilter;

                    if (chunktrackingview1 instanceof ChunkTrackingView.Positioned) {
                        ChunkTrackingView.Positioned chunktrackingview_a1 = (ChunkTrackingView.Positioned) chunktrackingview1;

                        if (chunktrackingview_a1.center().equals(chunktrackingview_a.center())) {
                            break label15;
                        }
                    }

                    player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunktrackingview_a.center().x, chunktrackingview_a.center().z));
                }
            }

            ChunkTrackingView.difference(chunktrackingview1, chunkFilter, (chunkcoordintpair) -> {
                this.markChunkPendingToSend(player, chunkcoordintpair);
            }, (chunkcoordintpair) -> {
                ChunkMap.dropChunk(player, chunkcoordintpair);
            });
            player.setChunkTrackingView(chunkFilter);
        }
    }

    @Override
    public List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge) {
        Set<ServerPlayer> set = this.playerMap.getAllPlayers();
        Builder<ServerPlayer> builder = ImmutableList.builder();
        Iterator iterator = set.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (onlyOnWatchDistanceEdge && this.isChunkOnTrackedBorder(entityplayer, chunkPos.x, chunkPos.z) || !onlyOnWatchDistanceEdge && this.isChunkTracked(entityplayer, chunkPos.x, chunkPos.z)) {
                builder.add(entityplayer);
            }
        }

        return builder.build();
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

    }

    protected void tick() {
        Iterator iterator = this.playerMap.getAllPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            this.updateChunkTracking(entityplayer);
        }

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
        int j = radius + 1;

        ChunkPos.rangeClosed(centerPos, j).forEach((chunkcoordintpair1) -> {
            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(chunkcoordintpair1.toLong());

            if (playerchunk != null) {
                playerchunk.addSendDependency(this.lightEngine.waitForPendingTasks(chunkcoordintpair1.x, chunkcoordintpair1.z));
            }

        });
    }

    public class ChunkDistanceManager extends DistanceManager { // Paper - public

        protected ChunkDistanceManager(final Executor workerExecutor, final Executor mainThreadExecutor) {
            super(workerExecutor, mainThreadExecutor);
        }

        @Override
        protected boolean isChunkToRemove(long pos) {
            return ChunkMap.this.toDrop.contains(pos);
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

    public class TrackedEntity {

        public final ServerEntity serverEntity;
        final Entity entity;
        private final int range;
        SectionPos lastSectionPos;
        public final Set<ServerPlayerConnection> seenBy = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(); // Paper - Perf: optimise map impl

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
                Vec3 vec3d = player.position().subtract(this.entity.position());
                int i = ChunkMap.this.getPlayerViewDistance(player);
                double d0 = (double) Math.min(this.getEffectiveRange(), i * 16);
                double d1 = vec3d.x * vec3d.x + vec3d.z * vec3d.z;
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
                if (!player.getBukkitEntity().canSee(this.entity.getBukkitEntity())) {
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
