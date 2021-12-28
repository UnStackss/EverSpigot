package net.minecraft.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import co.aikar.timings.Timings;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

// CraftBukkit start
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.util.Random;
// import jline.console.ConsoleReader; // Paper
import joptsimple.OptionSet;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.event.server.ServerLoadEvent;
// CraftBukkit end

import co.aikar.timings.MinecraftTimings; // Paper

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, ChunkIOErrorReporter, CommandSource, AutoCloseable {

    private static MinecraftServer SERVER; // Paper
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final net.kyori.adventure.text.logger.slf4j.ComponentLogger COMPONENT_LOGGER = net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(LOGGER.getName()); // Paper
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 30L * TimeUtil.NANOSECONDS_PER_SECOND / 20L; // CraftBukkit
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    private static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    private static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings("Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
    public static final GameProfile ANONYMOUS_PLAYER_PROFILE = new GameProfile(Util.NIL_UUID, "Anonymous Player");
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder;
    private ProfilerFiller profiler;
    private Consumer<ProfileResults> onMetricsRecordingStopped;
    private Consumer<Path> onMetricsRecordingFinished;
    private boolean willStartRecordingMetrics;
    @Nullable
    private MinecraftServer.TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private ServerConnectionListener connection;
    public final ChunkProgressListenerFactory progressListenerFactory;
    @Nullable
    private ServerStatus status;
    @Nullable
    private ServerStatus.Favicon statusIcon;
    private final RandomSource random;
    public final DataFixer fixerUpper;
    private String localIp;
    private int port;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private Map<ResourceKey<Level>, ServerLevel> levels;
    private PlayerList playerList;
    private volatile boolean running;
    private volatile boolean isRestarting = false; // Paper - flag to signify we're attempting to restart
    private boolean stopped;
    private int tickCount;
    private int ticksUntilAutosave;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    private net.kyori.adventure.text.Component motd; // Paper - Adventure
    private int playerIdleTimeout;
    private final long[] tickTimesNanos;
    private long aggregatedTickTimesNanos;
    // Paper start - Add tick times API and /mspt command
    public final TickTimes tickTimes5s = new TickTimes(100);
    public final TickTimes tickTimes10s = new TickTimes(200);
    public final TickTimes tickTimes60s = new TickTimes(1200);
    // Paper end - Add tick times API and /mspt command
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private long lastServerStatus;
    public final Thread serverThread;
    private long lastTickNanos;
    private long taskExecutionStartNanos;
    private long idleTimeNanos;
    private long nextTickTimeNanos;
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard;
    @Nullable
    private CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents;
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private float smoothedTickTimeMillis;
    public final Executor executor;
    @Nullable
    private String serverId;
    public MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    protected WorldData worldData;
    public PotionBrewing potionBrewing;
    private volatile boolean isSaving;
    private static final AtomicReference<RuntimeException> fatalException = new AtomicReference();

    // CraftBukkit start
    public final WorldLoader.DataLoadContext worldLoader;
    public org.bukkit.craftbukkit.CraftServer server;
    public OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    public static int currentTick; // Paper - improve tick loop
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    public Commands vanillaCommandDispatcher;
    private boolean forceTicks;
    // CraftBukkit end
    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / MinecraftServer.TPS;
    private static final int SAMPLE_INTERVAL = 20; // Paper - improve server tick loop
    @Deprecated(forRemoval = true) // Paper
    public final double[] recentTps = new double[ 3 ];
    // Spigot end
    public final io.papermc.paper.configuration.PaperConfigurations paperConfigurations; // Paper - add paper configuration files
    public static long currentTickLong = 0L; // Paper - track current tick as a long

    public static <S extends MinecraftServer> S spin(Function<Thread, S> serverFactory) {
        AtomicReference<S> atomicreference = new AtomicReference();
        Thread thread = new Thread(() -> {
            ((MinecraftServer) atomicreference.get()).runServer();
        }, "Server thread");

        thread.setUncaughtExceptionHandler((thread1, throwable) -> {
            MinecraftServer.LOGGER.error("Uncaught exception in server thread", throwable);
        });
        thread.setPriority(Thread.NORM_PRIORITY+2); // Paper - Perf: Boost priority
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S s0 = serverFactory.apply(thread); // CraftBukkit - decompile error

        atomicreference.set(s0);
        thread.start();
        return s0;
    }

    public MinecraftServer(OptionSet options, WorldLoader.DataLoadContext worldLoader, Thread thread, LevelStorageSource.LevelStorageAccess convertable_conversionsession, PackRepository resourcepackrepository, WorldStem worldstem, Proxy proxy, DataFixer datafixer, Services services, ChunkProgressListenerFactory worldloadlistenerfactory) {
        super("Server");
        SERVER = this; // Paper - better singleton
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
        this.profiler = this.metricsRecorder.getProfiler();
        this.onMetricsRecordingStopped = (methodprofilerresults) -> {
            this.stopRecordingMetrics();
        };
        this.onMetricsRecordingFinished = (path) -> {
        };
        this.random = RandomSource.create();
        this.port = -1;
        this.levels = Maps.newLinkedHashMap();
        this.running = true;
        this.ticksUntilAutosave = 6000;
        this.tickTimesNanos = new long[100];
        this.aggregatedTickTimesNanos = 0L;
        this.lastTickNanos = Util.getNanos();
        this.taskExecutionStartNanos = Util.getNanos();
        this.nextTickTimeNanos = Util.getNanos();
        this.scoreboard = new ServerScoreboard(this);
        this.customBossEvents = new CustomBossEvents();
        this.registries = worldstem.registries();
        this.worldData = worldstem.worldData();
        if (false && !this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) { // CraftBukkit - initialised later
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.proxy = proxy;
            this.packRepository = resourcepackrepository;
            this.resources = new MinecraftServer.ReloadableResources(worldstem.resourceManager(), worldstem.dataPackResources());
            this.services = services;
            if (services.profileCache() != null) {
                services.profileCache().setExecutor(this);
            }

            // this.connection = new ServerConnection(this); // Spigot
            this.tickRateManager = new ServerTickRateManager(this);
            this.progressListenerFactory = worldloadlistenerfactory;
            this.storageSource = convertable_conversionsession;
            this.playerDataStorage = convertable_conversionsession.createPlayerStorage();
            this.fixerUpper = datafixer;
            this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holdergetter = this.registries.compositeAccess().registryOrThrow(Registries.BLOCK).asLookup().filterFeatures(this.worldData.enabledFeatures());

            this.structureTemplateManager = new StructureTemplateManager(worldstem.resourceManager(), convertable_conversionsession, datafixer, holdergetter);
            this.serverThread = thread;
            this.executor = Util.backgroundExecutor();
            this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures());
        }
        // CraftBukkit start
        this.options = options;
        this.worldLoader = worldLoader;
        this.vanillaCommandDispatcher = worldstem.dataPackResources().commands; // CraftBukkit
        // Paper start - Handled by TerminalConsoleAppender
        // Try to see if we're actually running in a terminal, disable jline if not
        /*
        if (System.console() == null && System.getProperty("jline.terminal") == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            Main.useJline = false;
        }

        try {
            this.reader = new ConsoleReader(System.in, System.out);
            this.reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                Main.useJline = false;
                this.reader = new ConsoleReader(System.in, System.out);
                this.reader.setExpandEvents(false);
            } catch (IOException ex) {
                MinecraftServer.LOGGER.warn((String) null, ex);
            }
        }
        */
        // Paper end
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));
        // CraftBukkit end
        this.paperConfigurations = services.paperConfigurations(); // Paper - add paper configuration files
    }

    private void readScoreboard(DimensionDataStorage persistentStateManager) {
        persistentStateManager.computeIfAbsent(this.getScoreboard().dataFactory(), "scoreboard");
    }

    protected abstract boolean initServer() throws IOException;

    protected void loadLevel(String s) { // CraftBukkit
        if (!JvmProfiler.INSTANCE.isRunning()) {
            ;
        }

        boolean flag = false;
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onWorldLoadedStarted();

        this.loadWorld0(s); // CraftBukkit

        if (profiledduration != null) {
            profiledduration.finish();
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to stop JFR profiling", throwable);
            }
        }

    }

    protected void forceDifficulty() {}

    // CraftBukkit start
    private void loadWorld0(String s) {
        LevelStorageSource.LevelStorageAccess worldSession = this.storageSource;

        RegistryAccess.Frozen iregistrycustom_dimension = this.registries.compositeAccess();
        Registry<LevelStem> dimensions = iregistrycustom_dimension.registryOrThrow(Registries.LEVEL_STEM);
        for (LevelStem worldDimension : dimensions) {
            ResourceKey<LevelStem> dimensionKey = dimensions.getResourceKey(worldDimension).get();

            ServerLevel world;
            int dimension = 0;

            if (dimensionKey == LevelStem.NETHER) {
                if (this.server.getAllowNether()) {
                    dimension = -1;
                } else {
                    continue;
                }
            } else if (dimensionKey == LevelStem.END) {
                if (this.server.getAllowEnd()) {
                    dimension = 1;
                } else {
                    continue;
                }
            } else if (dimensionKey != LevelStem.OVERWORLD) {
                dimension = -999;
            }

            String worldType = (dimension == -999) ? dimensionKey.location().getNamespace() + "_" + dimensionKey.location().getPath() : org.bukkit.World.Environment.getEnvironment(dimension).toString().toLowerCase(Locale.ROOT);
            String name = (dimensionKey == LevelStem.OVERWORLD) ? s : s + "_" + worldType;
            if (dimension != 0) {
                File newWorld = LevelStorageSource.getStorageFolder(new File(name).toPath(), dimensionKey).toFile();
                File oldWorld = LevelStorageSource.getStorageFolder(new File(s).toPath(), dimensionKey).toFile();
                File oldLevelDat = new File(new File(s), "level.dat"); // The data folders exist on first run as they are created in the PersistentCollection constructor above, but the level.dat won't

                if (!newWorld.isDirectory() && oldWorld.isDirectory() && oldLevelDat.isFile()) {
                    MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder required ----");
                    MinecraftServer.LOGGER.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    MinecraftServer.LOGGER.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    MinecraftServer.LOGGER.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        MinecraftServer.LOGGER.warn("A file or folder already exists at " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            MinecraftServer.LOGGER.info("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);
                            // Migrate world data too.
                            try {
                                com.google.common.io.Files.copy(oldLevelDat, new File(new File(name), "level.dat"));
                                org.apache.commons.io.FileUtils.copyDirectory(new File(new File(s), "data"), new File(new File(name), "data"));
                            } catch (IOException exception) {
                                MinecraftServer.LOGGER.warn("Unable to migrate world data.");
                            }
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            MinecraftServer.LOGGER.warn("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        MinecraftServer.LOGGER.warn("Could not create path for " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                try {
                    worldSession = LevelStorageSource.createDefault(this.server.getWorldContainer().toPath()).validateAndCreateAccess(name, dimensionKey);
                } catch (IOException | ContentValidationException ex) {
                    throw new RuntimeException(ex);
                }
            }

            Dynamic<?> dynamic;
            if (worldSession.hasWorldData()) {
                LevelSummary worldinfo;

                try {
                    dynamic = worldSession.getDataTag();
                    worldinfo = worldSession.getSummary(dynamic);
                } catch (NbtException | ReportedNbtException | IOException ioexception) {
                    LevelStorageSource.LevelDirectory convertable_b = worldSession.getLevelDirectory();

                    MinecraftServer.LOGGER.warn("Failed to load world data from {}", convertable_b.dataFile(), ioexception);
                    MinecraftServer.LOGGER.info("Attempting to use fallback");

                    try {
                        dynamic = worldSession.getDataTagFallback();
                        worldinfo = worldSession.getSummary(dynamic);
                    } catch (NbtException | ReportedNbtException | IOException ioexception1) {
                        MinecraftServer.LOGGER.error("Failed to load world data from {}", convertable_b.oldDataFile(), ioexception1);
                        MinecraftServer.LOGGER.error("Failed to load world data from {} and {}. World files may be corrupted. Shutting down.", convertable_b.dataFile(), convertable_b.oldDataFile());
                        return;
                    }

                    worldSession.restoreLevelDataFromOld();
                }

                if (worldinfo.requiresManualConversion()) {
                    MinecraftServer.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                    return;
                }

                if (!worldinfo.isCompatible()) {
                    MinecraftServer.LOGGER.info("This world was created by an incompatible version.");
                    return;
                }
            } else {
                dynamic = null;
            }

            org.bukkit.generator.ChunkGenerator gen = this.server.getGenerator(name);
            org.bukkit.generator.BiomeProvider biomeProvider = this.server.getBiomeProvider(name);

            PrimaryLevelData worlddata;
            WorldLoader.DataLoadContext worldloader_a = this.worldLoader;
            Registry<LevelStem> iregistry = worldloader_a.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
            if (dynamic != null) {
                LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(dynamic, worldloader_a.dataConfiguration(), iregistry, worldloader_a.datapackWorldgen());

                worlddata = (PrimaryLevelData) leveldataanddimensions.worldData();
            } else {
                LevelSettings worldsettings;
                WorldOptions worldoptions;
                WorldDimensions worlddimensions;

                if (this.isDemo()) {
                    worldsettings = MinecraftServer.DEMO_SETTINGS;
                    worldoptions = WorldOptions.DEMO_OPTIONS;
                    worlddimensions = WorldPresets.createNormalWorldDimensions(worldloader_a.datapackWorldgen());
                } else {
                    DedicatedServerProperties dedicatedserverproperties = ((DedicatedServer) this).getProperties();

                    worldsettings = new LevelSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty, false, new GameRules(), worldloader_a.dataConfiguration());
                    worldoptions = this.options.has("bonusChest") ? dedicatedserverproperties.worldOptions.withBonusChest(true) : dedicatedserverproperties.worldOptions;
                    worlddimensions = dedicatedserverproperties.createDimensions(worldloader_a.datapackWorldgen());
                }

                WorldDimensions.Complete worlddimensions_b = worlddimensions.bake(iregistry);
                Lifecycle lifecycle = worlddimensions_b.lifecycle().add(worldloader_a.datapackWorldgen().allRegistriesLifecycle());

                worlddata = new PrimaryLevelData(worldsettings, worldoptions, worlddimensions_b.specialWorldProperty(), lifecycle);
            }
            worlddata.checkName(name); // CraftBukkit - Migration did not rewrite the level.dat; This forces 1.8 to take the last loaded world as respawn (in this case the end)
            if (this.options.has("forceUpgrade")) {
                net.minecraft.server.Main.forceUpgrade(worldSession, DataFixers.getDataFixer(), this.options.has("eraseCache"), () -> {
                    return true;
                }, iregistrycustom_dimension, this.options.has("recreateRegionFiles"));
            }

            PrimaryLevelData iworlddataserver = worlddata;
            boolean flag = worlddata.isDebugWorld();
            WorldOptions worldoptions = worlddata.worldGenOptions();
            long i = worldoptions.seed();
            long j = BiomeManager.obfuscateSeed(i);
            List<CustomSpawner> list = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(iworlddataserver));
            LevelStem worlddimension = (LevelStem) dimensions.get(dimensionKey);

            org.bukkit.generator.WorldInfo worldInfo = new org.bukkit.craftbukkit.generator.CraftWorldInfo(iworlddataserver, worldSession, org.bukkit.World.Environment.getEnvironment(dimension), worlddimension.type().value(), worlddimension.generator(), this.registryAccess()); // Paper - Expose vanilla BiomeProvider from WorldInfo
            if (biomeProvider == null && gen != null) {
                biomeProvider = gen.getDefaultBiomeProvider(worldInfo);
            }

            ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, dimensionKey.location());

            if (dimensionKey == LevelStem.OVERWORLD) {
                this.worldData = worlddata;
                this.worldData.setGameType(((DedicatedServer) this).getProperties().gamemode); // From DedicatedServer.init

                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));

                world = new ServerLevel(this, this.executor, worldSession, iworlddataserver, worldKey, worlddimension, worldloadlistener, flag, j, list, true, (RandomSequences) null, org.bukkit.World.Environment.getEnvironment(dimension), gen, biomeProvider);
                DimensionDataStorage worldpersistentdata = world.getDataStorage();
                this.readScoreboard(worldpersistentdata);
                this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, world.getScoreboard());
                this.commandStorage = new CommandStorage(worldpersistentdata);
            } else {
                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
                world = new ServerLevel(this, this.executor, worldSession, iworlddataserver, worldKey, worlddimension, worldloadlistener, flag, j, ImmutableList.of(), true, this.overworld().getRandomSequences(), org.bukkit.World.Environment.getEnvironment(dimension), gen, biomeProvider);
            }

            worlddata.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
            this.initWorld(world, worlddata, this.worldData, worldoptions);

            this.addLevel(world);
            this.getPlayerList().addWorldborderListener(world);

            if (worlddata.getCustomBossEvents() != null) {
                this.getCustomBossEvents().load(worlddata.getCustomBossEvents(), this.registryAccess());
            }
        }
        this.forceDifficulty();
        for (ServerLevel worldserver : this.getAllLevels()) {
            this.prepareLevels(worldserver.getChunkSource().chunkMap.progressListener, worldserver);
            worldserver.entityManager.tick(); // SPIGOT-6526: Load pending entities so they are available to the API
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(worldserver.getWorld()));
        }

        // Paper start - Configurable player collision; Handle collideRule team for player collision toggle
        final ServerScoreboard scoreboard = this.getScoreboard();
        final java.util.Collection<String> toRemove = scoreboard.getPlayerTeams().stream().filter(team -> team.getName().startsWith("collideRule_")).map(net.minecraft.world.scores.PlayerTeam::getName).collect(java.util.stream.Collectors.toList());
        for (String teamName : toRemove) {
            scoreboard.removePlayerTeam(scoreboard.getPlayerTeam(teamName)); // Clean up after ourselves
        }

        if (!io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions) {
            this.getPlayerList().collideRuleTeamName = org.apache.commons.lang3.StringUtils.left("collideRule_" + java.util.concurrent.ThreadLocalRandom.current().nextInt(), 16);
            net.minecraft.world.scores.PlayerTeam collideTeam = scoreboard.addPlayerTeam(this.getPlayerList().collideRuleTeamName);
            collideTeam.setSeeFriendlyInvisibles(false); // Because we want to mimic them not being on a team at all
        }
        // Paper end - Configurable player collision

        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.pluginsEnabled(); // Paper - Remap plugins
        this.server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
    }

    public void initWorld(ServerLevel worldserver, ServerLevelData iworlddataserver, WorldData saveData, WorldOptions worldoptions) {
        boolean flag = saveData.isDebugWorld();
        // CraftBukkit start
        if (worldserver.generator != null) {
            worldserver.getWorld().getPopulators().addAll(worldserver.generator.getDefaultPopulators(worldserver.getWorld()));
        }
        WorldBorder worldborder = worldserver.getWorldBorder();
        worldborder.applySettings(iworlddataserver.getWorldBorder()); // CraftBukkit - move up so that WorldBorder is set during WorldInitEvent
        this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(worldserver.getWorld())); // CraftBukkit - SPIGOT-5569: Call WorldInitEvent before any chunks are generated

        if (!iworlddataserver.isInitialized()) {
            try {
                MinecraftServer.setInitialSpawn(worldserver, iworlddataserver, worldoptions.generateBonusChest(), flag);
                iworlddataserver.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception initializing level");

                try {
                    worldserver.fillReportDetails(crashreport);
                } catch (Throwable throwable1) {
                    ;
                }

                throw new ReportedException(crashreport);
            }

            iworlddataserver.setInitialized(true);
        }

    }
    // CraftBukkit end

    private static void setInitialSpawn(ServerLevel world, ServerLevelData worldProperties, boolean bonusChest, boolean debugWorld) {
        if (debugWorld) {
            worldProperties.setSpawn(BlockPos.ZERO.above(80), 0.0F);
        } else {
            ServerChunkCache chunkproviderserver = world.getChunkSource();
            ChunkPos chunkcoordintpair = new ChunkPos(chunkproviderserver.randomState().sampler().findSpawnPosition());
            // CraftBukkit start
            if (world.generator != null) {
                Random rand = new Random(world.getSeed());
                org.bukkit.Location spawn = world.generator.getFixedSpawnLocation(world.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != world.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + worldProperties.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        worldProperties.setSpawn(new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()), spawn.getYaw());
                        return;
                    }
                }
            }
            // CraftBukkit end
            int i = chunkproviderserver.getGenerator().getSpawnHeight(world);

            if (i < world.getMinBuildHeight()) {
                BlockPos blockposition = chunkcoordintpair.getWorldPosition();

                i = world.getHeight(Heightmap.Types.WORLD_SURFACE, blockposition.getX() + 8, blockposition.getZ() + 8);
            }

            worldProperties.setSpawn(chunkcoordintpair.getWorldPosition().offset(8, i, 8), 0.0F);
            int j = 0;
            int k = 0;
            int l = 0;
            int i1 = -1;

            for (int j1 = 0; j1 < Mth.square(11); ++j1) {
                if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
                    BlockPos blockposition1 = PlayerRespawnLogic.getSpawnPosInChunk(world, new ChunkPos(chunkcoordintpair.x + j, chunkcoordintpair.z + k));

                    if (blockposition1 != null) {
                        worldProperties.setSpawn(blockposition1, 0.0F);
                        break;
                    }
                }

                if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
                    int k1 = l;

                    l = -i1;
                    i1 = k1;
                }

                j += l;
                k += i1;
            }

            if (bonusChest) {
                world.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((iregistry) -> {
                    return iregistry.getHolder(MiscOverworldFeatures.BONUS_CHEST);
                }).ifPresent((holder_c) -> {
                    ((ConfiguredFeature) holder_c.value()).place(world, chunkproviderserver.getGenerator(), world.random, worldProperties.getSpawnPos());
                });
            }

        }
    }

    private void setupDebugLevel(WorldData properties) {
        properties.setDifficulty(Difficulty.PEACEFUL);
        properties.setDifficultyLocked(true);
        ServerLevelData iworlddataserver = properties.overworldData();

        iworlddataserver.setRaining(false);
        iworlddataserver.setThundering(false);
        iworlddataserver.setClearWeatherTime(1000000000);
        iworlddataserver.setDayTime(6000L);
        iworlddataserver.setGameType(GameType.SPECTATOR);
    }

    // CraftBukkit start
    public void prepareLevels(ChunkProgressListener worldloadlistener, ServerLevel worldserver) {
        // WorldServer worldserver = this.overworld();
        this.forceTicks = true;
        // CraftBukkit end

        MinecraftServer.LOGGER.info("Preparing start region for dimension {}", worldserver.dimension().location());
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        worldloadlistener.updateSpawnPos(new ChunkPos(blockposition));
        ServerChunkCache chunkproviderserver = worldserver.getChunkSource();

        this.nextTickTimeNanos = Util.getNanos();
        worldserver.setDefaultSpawnPos(blockposition, worldserver.getSharedSpawnAngle());
        int i = worldserver.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS); // CraftBukkit - per-world
        int j = i > 0 ? Mth.square(ChunkProgressListener.calculateDiameter(i)) : 0;

        while (chunkproviderserver.getTickingGenerated() < j) {
            // CraftBukkit start
            // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.executeModerately();
        }

        // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.executeModerately();
        // Iterator iterator = this.levels.values().iterator();

        if (true) {
            ServerLevel worldserver1 = worldserver;
            // CraftBukkit end
            ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) worldserver1.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");

            if (forcedchunk != null) {
                LongIterator longiterator = forcedchunk.getChunks().iterator();

                while (longiterator.hasNext()) {
                    long k = longiterator.nextLong();
                    ChunkPos chunkcoordintpair = new ChunkPos(k);

                    worldserver1.getChunkSource().updateChunkForced(chunkcoordintpair, true);
                }
            }
        }

        // CraftBukkit start
        // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.executeModerately();
        // CraftBukkit end
        worldloadlistener.stop();
        // CraftBukkit start
        // this.updateMobSpawningFlags();
        worldserver.setSpawnSettings(worldserver.serverLevelData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))

        this.forceTicks = false;
        // CraftBukkit end
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean suppressLogs, boolean flush, boolean force) {
        boolean flag3 = false;

        for (Iterator iterator = this.getAllLevels().iterator(); iterator.hasNext(); flag3 = true) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (!suppressLogs) {
                MinecraftServer.LOGGER.info("Saving chunks for level '{}'/{}", worldserver, worldserver.dimension().location());
            }

            worldserver.save((ProgressListener) null, flush, worldserver.noSave && !force);
        }

        // CraftBukkit start - moved to WorldServer.save
        /*
        WorldServer worldserver1 = this.overworld();
        IWorldDataServer iworlddataserver = this.worldData.overworldData();

        iworlddataserver.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        */
        // CraftBukkit end
        if (flush) {
            Iterator iterator1 = this.getAllLevels().iterator();

            while (iterator1.hasNext()) {
                ServerLevel worldserver2 = (ServerLevel) iterator1.next();

                MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", worldserver2.getChunkSource().chunkMap.getStorageName());
            }

            MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }

        return flag3;
    }

    public boolean saveEverything(boolean suppressLogs, boolean flush, boolean force) {
        boolean flag3;

        try {
            this.isSaving = true;
            this.getPlayerList().saveAll();
            flag3 = this.saveAllChunks(suppressLogs, flush, force);
        } finally {
            this.isSaving = false;
        }

        return flag3;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // CraftBukkit start
    private boolean hasStopped = false;
    private boolean hasLoggedStop = false; // Paper - Debugging
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (this.stopLock) {
            return this.hasStopped;
        }
    }
    // CraftBukkit end

    public void stopServer() {
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(this.stopLock) {
            if (this.hasStopped) return;
            this.hasStopped = true;
        }
        if (!hasLoggedStop && isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        // CraftBukkit end
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        MinecraftServer.LOGGER.info("Stopping server");
        Commands.COMMAND_SENDING_POOL.shutdownNow(); // Paper - Perf: Async command map building; Shutdown and don't bother finishing
        MinecraftTimings.stopServer(); // Paper
        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
            this.server.waitForAsyncTasksShutdown(); // Paper - Wait for Async Tasks during shutdown
        }
        // CraftBukkit end
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.shutdown(); // Paper - Plugin remapping
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            MinecraftServer.LOGGER.info("Saving players");
            this.playerList.saveAll();
            this.playerList.removeAll(this.isRestarting); // Paper
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        MinecraftServer.LOGGER.info("Saving worlds");
        Iterator iterator = this.getAllLevels().iterator();

        ServerLevel worldserver;

        while (iterator.hasNext()) {
            worldserver = (ServerLevel) iterator.next();
            if (worldserver != null) {
                worldserver.noSave = false;
            }
        }

        while (this.levels.values().stream().anyMatch((worldserver1) -> {
            return worldserver1.getChunkSource().chunkMap.hasWork();
        })) {
            this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;
            iterator = this.getAllLevels().iterator();

            while (iterator.hasNext()) {
                worldserver = (ServerLevel) iterator.next();
                worldserver.getChunkSource().removeTicketsOnClosing();
                worldserver.getChunkSource().tick(() -> {
                    return true;
                }, false);
            }

            this.waitUntilNextTick();
        }

        this.saveAllChunks(false, true, false);
        iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            worldserver = (ServerLevel) iterator.next();
            if (worldserver != null) {
                try {
                    worldserver.close();
                } catch (IOException ioexception) {
                    MinecraftServer.LOGGER.error("Exception closing the level", ioexception);
                }
            }
        }

        this.isSaving = false;
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException ioexception1) {
            MinecraftServer.LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), ioexception1);
        }
        // Spigot start
        io.papermc.paper.util.MCUtil.asyncExecutor.shutdown(); // Paper
        try { io.papermc.paper.util.MCUtil.asyncExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); // Paper
        } catch (java.lang.InterruptedException ignored) {} // Paper
        if (org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) {
            MinecraftServer.LOGGER.info("Saving usercache.json");
            this.getProfileCache().save(false); // Paper - Perf: Async GameProfileCache saving
        }
        // Spigot end

    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String serverIp) {
        this.localIp = serverIp;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean waitForShutdown) {
        // Paper start - allow passing of the intent to restart
        this.safeShutdown(waitForShutdown, false);
    }
    public void safeShutdown(boolean waitForShutdown, boolean isRestarting) {
        this.isRestarting = isRestarting;
        this.hasLoggedStop = true; // Paper - Debugging
        if (isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        // Paper end
        this.running = false;
        if (waitForShutdown) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedexception) {
                MinecraftServer.LOGGER.error("Error while shutting down", interruptedexception);
            }
        }

    }

    // Spigot Start
    private static double calcTps(double avg, double exp, double tps)
    {
        return ( avg * exp ) + ( tps * ( 1 - exp ) );
    }

    // Paper start - Further improve server tick loop
    private static final long SEC_IN_NANO = 1000000000;
    private static final long MAX_CATCHUP_BUFFER = TICK_TIME * TPS * 60L;
    private long lastTick = 0;
    private long catchupTime = 0;
    public final RollingAverage tps1 = new RollingAverage(60);
    public final RollingAverage tps5 = new RollingAverage(60 * 5);
    public final RollingAverage tps15 = new RollingAverage(60 * 15);

    public static class RollingAverage {
        private final int size;
        private long time;
        private java.math.BigDecimal total;
        private int index = 0;
        private final java.math.BigDecimal[] samples;
        private final long[] times;

        RollingAverage(int size) {
            this.size = size;
            this.time = size * SEC_IN_NANO;
            this.total = dec(TPS).multiply(dec(SEC_IN_NANO)).multiply(dec(size));
            this.samples = new java.math.BigDecimal[size];
            this.times = new long[size];
            for (int i = 0; i < size; i++) {
                this.samples[i] = dec(TPS);
                this.times[i] = SEC_IN_NANO;
            }
        }

        private static java.math.BigDecimal dec(long t) {
            return new java.math.BigDecimal(t);
        }
        public void add(java.math.BigDecimal x, long t) {
            time -= times[index];
            total = total.subtract(samples[index].multiply(dec(times[index])));
            samples[index] = x;
            times[index] = t;
            time += t;
            total = total.add(x.multiply(dec(t)));
            if (++index == size) {
                index = 0;
            }
        }

        public double getAverage() {
            return total.divide(dec(time), 30, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
    private static final java.math.BigDecimal TPS_BASE = new java.math.BigDecimal(1E9).multiply(new java.math.BigDecimal(SAMPLE_INTERVAL));
    // Paper end
    // Spigot End

    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = Util.getNanos();
            this.statusIcon = (ServerStatus.Favicon) this.loadStatusIcon().orElse(null); // CraftBukkit - decompile error
            this.status = this.buildServerStatus();

            // Spigot start
            org.spigotmc.WatchdogThread.hasStarted = true; // Paper
            Arrays.fill( this.recentTps, 20 );
            // Paper start - further improve server tick loop
            long tickSection = Util.getNanos();
            long currentTime;
            // Paper end - further improve server tick loop
            while (this.running) {
                long i;

                if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
                    i = 0L;
                    this.nextTickTimeNanos = Util.getNanos();
                    this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                } else {
                    i = this.tickRateManager.nanosecondsPerTick();
                    long j = Util.getNanos() - this.nextTickTimeNanos;

                    if (j > MinecraftServer.OVERLOADED_THRESHOLD_NANOS + 20L * i && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= MinecraftServer.OVERLOADED_WARNING_INTERVAL_NANOS + 100L * i) {
                        long k = j / i;

                        if (this.server.getWarnOnOverload()) // CraftBukkit
                        MinecraftServer.LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", j / TimeUtil.NANOSECONDS_PER_MILLISECOND, k);
                        this.nextTickTimeNanos += k * i;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }
                // Spigot start
                ++MinecraftServer.currentTickLong; // Paper - track current tick as a long
                // Paper start - further improve server tick loop
                currentTime = Util.getNanos();
                if (++MinecraftServer.currentTick % MinecraftServer.SAMPLE_INTERVAL == 0) {
                    final long diff = currentTime - tickSection;
                    final java.math.BigDecimal currentTps = TPS_BASE.divide(new java.math.BigDecimal(diff), 30, java.math.RoundingMode.HALF_UP);
                    tps1.add(currentTps, diff);
                    tps5.add(currentTps, diff);
                    tps15.add(currentTps, diff);

                    // Backwards compat with bad plugins
                    this.recentTps[0] = tps1.getAverage();
                    this.recentTps[1] = tps5.getAverage();
                    this.recentTps[2] = tps15.getAverage();
                    tickSection = currentTime;
                }
                // Paper end - further improve server tick loop
                // Spigot end

                boolean flag = i == 0L;

                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount);
                }

                //MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit // Paper - don't overwrite current tick time
                lastTick = currentTime;
                this.nextTickTimeNanos += i;
                this.startMetricsRecordingTick();
                this.profiler.push("tick");
                this.tickServer(flag ? () -> {
                    return false;
                } : this::haveTime);
                this.profiler.popPush("nextTickWait");
                this.mayHaveDelayedTasks = true;
                this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + i, this.nextTickTimeNanos);
                this.startMeasuringTaskExecutionTime();
                this.waitUntilNextTick();
                this.finishMeasuringTaskExecutionTime();
                if (flag) {
                    this.tickRateManager.endTickWork();
                }

                this.profiler.pop();
                this.logFullTickTime();
                this.endMetricsRecordingTick();
                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
            }
        } catch (Throwable throwable) {
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
            CrashReport crashreport = MinecraftServer.constructOrExtractCrashReport(throwable);

            this.fillSystemReport(crashreport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

            if (crashreport.saveToFile(path, ReportType.CRASH)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable1) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable1);
            } finally {
                if (this.services.profileCache() != null) {
                    this.services.profileCache().clearExecutor();
                }

                org.spigotmc.WatchdogThread.doStop(); // Spigot
                // CraftBukkit start - Restore terminal to original settings
                try {
                    net.minecrell.terminalconsole.TerminalConsoleAppender.close(); // Paper - Use TerminalConsoleAppender
                } catch (Exception ignored) {
                }
                // CraftBukkit end
                this.onServerExit();
            }

        }

    }

    private void logFullTickTime() {
        long i = Util.getNanos();

        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(i - this.lastTickNanos);
        }

        this.lastTickNanos = i;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }

    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger samplelogger = this.getTickTimeLogger();

            samplelogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            samplelogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }

    }

    private static CrashReport constructOrExtractCrashReport(Throwable throwable) {
        ReportedException reportedexception = null;

        for (Throwable throwable1 = throwable; throwable1 != null; throwable1 = throwable1.getCause()) {
            if (throwable1 instanceof ReportedException reportedexception1) {
                reportedexception = reportedexception1;
            }
        }

        CrashReport crashreport;

        if (reportedexception != null) {
            crashreport = reportedexception.getReport();
            if (reportedexception != throwable) {
                crashreport.addCategory("Wrapped in").setDetailError("Wrapping exception", throwable);
            }
        } else {
            crashreport = new CrashReport("Exception in server tick loop", throwable);
        }

        return crashreport;
    }

    private boolean haveTime() {
        // CraftBukkit start
        if (isOversleep) return canOversleep(); // Paper - because of our changes, this logic is broken
        return this.forceTicks || this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    // Paper start
    boolean isOversleep = false;
    private boolean canOversleep() {
        return this.mayHaveDelayedTasks && Util.getNanos() < this.delayedTasksMaxNextTickTimeNanos;
    }

    private boolean canSleepForTickNoOversleep() {
        return this.forceTicks || this.runningTask() || Util.getNanos() < this.nextTickTimeNanos;
    }
    // Paper end

    private void executeModerately() {
        this.runAllTasks();
        java.util.concurrent.locks.LockSupport.parkNanos("executing tasks", 1000L);
        // CraftBukkit end
    }

    public static boolean throwIfFatalException() {
        RuntimeException runtimeexception = (RuntimeException) MinecraftServer.fatalException.get();

        if (runtimeexception != null) {
            throw runtimeexception;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException exception) {
        MinecraftServer.fatalException.compareAndSet(null, exception); // CraftBukkit - decompile error
    }

    @Override
    public void managedBlock(BooleanSupplier stopCondition) {
        super.managedBlock(() -> {
            return MinecraftServer.throwIfFatalException() && stopCondition.getAsBoolean();
        });
    }

    protected void waitUntilNextTick() {
        //this.executeAll(); // Paper - move this into the tick method for timings
        this.managedBlock(() -> {
            return !this.canSleepForTickNoOversleep(); // Paper - move oversleep into full server tick
        });
    }

    @Override
    public void waitForTasks() {
        boolean flag = this.isTickTimeLoggingEnabled();
        long i = flag ? Util.getNanos() : 0L;

        super.waitForTasks();
        if (flag) {
            this.idleTimeNanos += Util.getNanos() - i;
        }

    }

    @Override
    public TickTask wrapRunnable(Runnable runnable) {
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean shouldRun(TickTask ticktask) {
        return ticktask.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    public boolean pollTask() {
        boolean flag = this.pollTaskInternal();

        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            return true;
        } else {
            boolean ret = false; // Paper - force execution of all worlds, do not just bias the first
            if (this.tickRateManager.isSprinting() || this.haveTime()) {
                Iterator iterator = this.getAllLevels().iterator();

                while (iterator.hasNext()) {
                    ServerLevel worldserver = (ServerLevel) iterator.next();

                    if (worldserver.getChunkSource().pollTask()) {
                        ret = true; // Paper - force execution of all worlds, do not just bias the first
                    }
                }
            }

            return ret; // Paper - force execution of all worlds, do not just bias the first
        }
    }

    public void doRunTask(TickTask ticktask) { // CraftBukkit - decompile error
        this.getProfiler().incrementCounter("runTask");
        super.doRunTask(ticktask);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png")).filter((path) -> {
            return Files.isRegularFile(path, new LinkOption[0]);
        }).or(() -> {
            return this.storageSource.getIconFile().filter((path) -> {
                return Files.isRegularFile(path, new LinkOption[0]);
            });
        });

        return optional.flatMap((path) -> {
            try {
                BufferedImage bufferedimage = ImageIO.read(path.toFile());

                Preconditions.checkState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                Preconditions.checkState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();

                ImageIO.write(bufferedimage, "PNG", bytearrayoutputstream);
                return Optional.of(new ServerStatus.Favicon(bytearrayoutputstream.toByteArray()));
            } catch (Exception exception) {
                MinecraftServer.LOGGER.error("Couldn't load server icon", exception);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    public void onServerCrash(CrashReport report) {}

    public void onServerExit() {}

    public boolean isPaused() {
        return false;
    }

    public void tickServer(BooleanSupplier shouldKeepTicking) {
        co.aikar.timings.TimingsManager.FULL_SERVER_TICK.startTiming(); // Paper
        long i = Util.getNanos();

        // Paper start - move oversleep into full server tick
        isOversleep = true;MinecraftTimings.serverOversleep.startTiming();
        this.managedBlock(() -> {
            return !this.canOversleep();
        });
        isOversleep = false;MinecraftTimings.serverOversleep.stopTiming();
        // Paper end
        new com.destroystokyo.paper.event.server.ServerTickStartEvent(this.tickCount+1).callEvent(); // Paper - Server Tick Events

        ++this.tickCount;
        this.tickRateManager.tick();
        this.tickChildren(shouldKeepTicking);
        if (i - this.lastServerStatus >= MinecraftServer.STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = i;
            this.status = this.buildServerStatus();
        }

        --this.ticksUntilAutosave;
        // CraftBukkit start
        if (this.autosavePeriod > 0 && this.ticksUntilAutosave <= 0) {
            this.ticksUntilAutosave = this.autosavePeriod;
            // CraftBukkit end
            MinecraftServer.LOGGER.debug("Autosave started");
            this.profiler.push("save");
            this.saveEverything(true, false, false);
            this.profiler.pop();
            MinecraftServer.LOGGER.debug("Autosave finished");
        }
        // Paper start - move executeAll() into full server tick timing
        try (co.aikar.timings.Timing ignored = MinecraftTimings.processTasksTimer.startTiming()) {
            this.runAllTasks();
        }
        // Paper end
        // Paper start - Server Tick Events
        long endTime = System.nanoTime();
        long remaining = (TICK_TIME - (endTime - lastTick)) - catchupTime;
        new com.destroystokyo.paper.event.server.ServerTickEndEvent(this.tickCount, ((double)(endTime - lastTick) / 1000000D), remaining).callEvent();
        // Paper end - Server Tick Events
        this.profiler.push("tallying");
        long j = Util.getNanos() - i;
        int k = this.tickCount % 100;

        this.aggregatedTickTimesNanos -= this.tickTimesNanos[k];
        this.aggregatedTickTimesNanos += j;
        this.tickTimesNanos[k] = j;
        this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float) j / (float) TimeUtil.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
        // Paper start - Add tick times API and /mspt command
        this.tickTimes5s.add(this.tickCount, j);
        this.tickTimes10s.add(this.tickCount, j);
        this.tickTimes60s.add(this.tickCount, j);
        // Paper end - Add tick times API and /mspt command
        this.logTickMethodTime(i);
        this.profiler.pop();
        org.spigotmc.WatchdogThread.tick(); // Spigot
        co.aikar.timings.TimingsManager.FULL_SERVER_TICK.stopTiming(); // Paper
    }

    private void logTickMethodTime(long tickStartTime) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(Util.getNanos() - tickStartTime, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }

    }

    private int computeNextAutosaveInterval() {
        float f;

        if (this.tickRateManager.isSprinting()) {
            long i = this.getAverageTickTimeNanos() + 1L;

            f = (float) TimeUtil.NANOSECONDS_PER_SECOND / (float) i;
        } else {
            f = this.tickRateManager.tickrate();
        }

        boolean flag = true;

        return Math.max(100, (int) (f * 300.0F));
    }

    public void onTickRateChanged() {
        int i = this.computeNextAutosaveInterval();

        if (i < this.ticksUntilAutosave) {
            this.ticksUntilAutosave = i;
        }

    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    private ServerStatus buildServerStatus() {
        ServerStatus.Players serverping_serverpingplayersample = this.buildPlayerStatus();

        return new ServerStatus(io.papermc.paper.adventure.PaperAdventure.asVanilla(this.motd), Optional.of(serverping_serverpingplayersample), Optional.of(ServerStatus.Version.current()), Optional.ofNullable(this.statusIcon), this.enforceSecureProfile()); // Paper - Adventure
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> list = this.playerList.getPlayers();
        int i = this.getMaxPlayers();

        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(i, list.size(), List.of());
        } else {
            int j = Math.min(list.size(), org.spigotmc.SpigotConfig.playerSample); // Paper - PaperServerListPingEvent
            ObjectArrayList<GameProfile> objectarraylist = new ObjectArrayList(j);
            int k = Mth.nextInt(this.random, 0, list.size() - j);

            for (int l = 0; l < j; ++l) {
                ServerPlayer entityplayer = (ServerPlayer) list.get(k + l);

                objectarraylist.add(entityplayer.allowsListing() ? entityplayer.getGameProfile() : MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
            }

            Util.shuffle(objectarraylist, this.random);
            return new ServerStatus.Players(i, list.size(), objectarraylist);
        }
    }

    public void tickChildren(BooleanSupplier shouldKeepTicking) {
        this.getPlayerList().getPlayers().forEach((entityplayer) -> {
            entityplayer.connection.suspendFlushing();
        });
        MinecraftTimings.bukkitSchedulerTimer.startTiming(); // Spigot // Paper
        this.server.getScheduler().mainThreadHeartbeat(this.tickCount); // CraftBukkit
        MinecraftTimings.bukkitSchedulerTimer.stopTiming(); // Spigot // Paper
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.CALLBACK_MANAGER.handleQueue(this.tickCount); // Paper
        this.profiler.push("commandFunctions");
        MinecraftTimings.commandFunctionsTimer.startTiming(); // Spigot // Paper
        this.getFunctions().tick();
        MinecraftTimings.commandFunctionsTimer.stopTiming(); // Spigot // Paper
        this.profiler.popPush("levels");
        Iterator iterator = this.getAllLevels().iterator();

        // CraftBukkit start
        // Run tasks that are waiting on processing
        MinecraftTimings.processQueueTimer.startTiming(); // Spigot
        while (!this.processQueue.isEmpty()) {
            this.processQueue.remove().run();
        }
        MinecraftTimings.processQueueTimer.stopTiming(); // Spigot

        MinecraftTimings.timeUpdateTimer.startTiming(); // Spigot // Paper
        // Send time updates to everyone, it will get the right time from the world the player is in.
        // Paper start - Perf: Optimize time updates
        for (final ServerLevel level : this.getAllLevels()) {
            final boolean doDaylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            final long dayTime = level.getDayTime();
            long worldTime = level.getGameTime();
            final ClientboundSetTimePacket worldPacket = new ClientboundSetTimePacket(worldTime, dayTime, doDaylight);
            for (Player entityhuman : level.players()) {
                if (!(entityhuman instanceof ServerPlayer) || (tickCount + entityhuman.getId()) % 20 != 0) {
                    continue;
                }
                ServerPlayer entityplayer = (ServerPlayer) entityhuman;
                long playerTime = entityplayer.getPlayerTime();
                ClientboundSetTimePacket packet = (playerTime == dayTime) ? worldPacket :
                    new ClientboundSetTimePacket(worldTime, playerTime, doDaylight);
                entityplayer.connection.send(packet); // Add support for per player time
            }
        }
        // Paper end - Perf: Optimize time updates
        MinecraftTimings.timeUpdateTimer.stopTiming(); // Spigot // Paper

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();
            worldserver.hasPhysicsEvent = org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - BlockPhysicsEvent
            worldserver.hasEntityMoveEvent = io.papermc.paper.event.entity.EntityMoveEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - Add EntityMoveEvent

            this.profiler.push(() -> {
                String s = String.valueOf(worldserver);

                return s + " " + String.valueOf(worldserver.dimension().location());
            });
            /* Drop global time updates
            if (this.tickCount % 20 == 0) {
                this.profiler.push("timeSync");
                this.synchronizeTime(worldserver);
                this.profiler.pop();
            }
            // CraftBukkit end */

            this.profiler.push("tick");

            try {
                worldserver.timings.doTick.startTiming(); // Spigot
                worldserver.tick(shouldKeepTicking);
                worldserver.timings.doTick.stopTiming(); // Spigot
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception ticking world");

                worldserver.fillReportDetails(crashreport);
                throw new ReportedException(crashreport);
            }

            this.profiler.pop();
            this.profiler.pop();
            worldserver.explosionDensityCache.clear(); // Paper - Optimize explosions
        }

        this.profiler.popPush("connection");
        MinecraftTimings.connectionTimer.startTiming(); // Spigot // Paper
        this.getConnection().tick();
        MinecraftTimings.connectionTimer.stopTiming(); // Spigot // Paper
        this.profiler.popPush("players");
        MinecraftTimings.playerListTimer.startTiming(); // Spigot // Paper
        this.playerList.tick();
        MinecraftTimings.playerListTimer.stopTiming(); // Spigot // Paper
        if (SharedConstants.IS_RUNNING_IN_IDE && this.tickRateManager.runsNormally()) {
            GameTestTicker.SINGLETON.tick();
        }

        this.profiler.popPush("server gui refresh");

        MinecraftTimings.tickablesTimer.startTiming(); // Spigot // Paper
        for (int i = 0; i < this.tickables.size(); ++i) {
            ((Runnable) this.tickables.get(i)).run();
        }
        MinecraftTimings.tickablesTimer.stopTiming(); // Spigot // Paper

        this.profiler.popPush("send chunks");
        iterator = this.playerList.getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.chunkSender.sendNextChunks(entityplayer);
            entityplayer.connection.resumeFlushing();
        }

        this.profiler.pop();
    }

    private void synchronizeTime(ServerLevel world) {
        this.playerList.broadcastAll(new ClientboundSetTimePacket(world.getGameTime(), world.getDayTime(), world.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)), world.dimension());
    }

    public void forceTimeSynchronization() {
        this.profiler.push("timeSync");
        Iterator iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            this.synchronizeTime(worldserver);
        }

        this.profiler.pop();
    }

    public boolean isLevelEnabled(Level world) {
        return true;
    }

    public void addTickable(Runnable tickable) {
        this.tickables.add(tickable);
    }

    protected void setId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String path) {
        return this.getServerDirectory().resolve(path);
    }

    public final ServerLevel overworld() {
        return (ServerLevel) this.levels.get(Level.OVERWORLD);
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> key) {
        return (ServerLevel) this.levels.get(key);
    }

    // CraftBukkit start
    public void addLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.put(level.dimension(), level);
        this.levels = Collections.unmodifiableMap(newLevels);
    }

    public void removeLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.remove(level.dimension());
        this.levels = Collections.unmodifiableMap(newLevels);
    }
    // CraftBukkit end

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return io.papermc.paper.ServerBuildInfo.buildInfo().brandName(); // Paper
    }

    public SystemReport fillSystemReport(SystemReport details) {
        details.setDetail("Server Running", () -> {
            return Boolean.toString(this.running);
        });
        if (this.playerList != null) {
            details.setDetail("Player Count", () -> {
                int i = this.playerList.getPlayerCount();

                return "" + i + " / " + this.playerList.getMaxPlayers() + "; " + String.valueOf(this.playerList.getPlayers());
            });
        }

        details.setDetail("Active Data Packs", () -> {
            return PackRepository.displayPackList(this.packRepository.getSelectedPacks());
        });
        details.setDetail("Available Data Packs", () -> {
            return PackRepository.displayPackList(this.packRepository.getAvailablePacks());
        });
        details.setDetail("Enabled Feature Flags", () -> {
            return (String) FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
        });
        details.setDetail("World Generation", () -> {
            return this.worldData.worldGenSettingsLifecycle().toString();
        });
        details.setDetail("World Seed", () -> {
            return String.valueOf(this.worldData.worldGenOptions().seed());
        });
        if (this.serverId != null) {
            details.setDetail("Server Id", () -> {
                return this.serverId;
            });
        }

        return this.fillServerSystemReport(details);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport details);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component message) {
        MinecraftServer.LOGGER.info(io.papermc.paper.adventure.PaperAdventure.ANSI_SERIALIZER.serialize(io.papermc.paper.adventure.PaperAdventure.asAdventure(message))); // Paper - Log message with colors
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int serverPort) {
        this.port = serverPort;
    }

    @Nullable
    public GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile hostProfile) {
        this.singleplayerProfile = hostProfile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        MinecraftServer.LOGGER.info("Generating keypair");

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException cryptographyexception) {
            throw new IllegalStateException("Failed to generate key pair", cryptographyexception);
        }
    }

    // Paper start - per level difficulty
    public void setDifficulty(ServerLevel level, Difficulty difficulty, boolean forceUpdate) {
        PrimaryLevelData worldData = level.serverLevelData;
        if (forceUpdate || !worldData.isDifficultyLocked()) {
            worldData.setDifficulty(worldData.isHardcore() ? Difficulty.HARD : difficulty);
            level.setSpawnSettings(worldData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals());
            // this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
            // Paper end - per level difficulty
        }
    }

    public int getScaledTrackingDistance(int initialDistance) {
        return initialDistance;
    }

    private void updateMobSpawningFlags() {
        Iterator iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            worldserver.setSpawnSettings(worldserver.serverLevelData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))
        }

    }

    public void setDifficultyLocked(boolean locked) {
        this.worldData.setDifficultyLocked(locked);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer player) {
        LevelData worlddata = player.level().getLevelData();

        player.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean demo) {
        this.isDemo = demo;
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean preventProxyConnections) {
        this.preventProxyConnections = preventProxyConnections;
    }

    public boolean isSpawningAnimals() {
        return true;
    }

    public boolean areNpcsEnabled() {
        return true;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean pvpEnabled) {
        this.pvp = pvpEnabled;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean flightEnabled) {
        this.allowFlight = flightEnabled;
    }

    public abstract boolean isCommandBlockEnabled();

    @Override
    public String getMotd() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(this.motd); // Paper - Adventure
    }

    public void setMotd(String motd) {
        // Paper start - Adventure
        this.motd = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserializeOr(motd, net.kyori.adventure.text.Component.empty());
    }

    public net.kyori.adventure.text.Component motd() {
        return this.motd;
    }

    public void motd(net.kyori.adventure.text.Component motd) {
        // Paper end - Adventure
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList playerManager) {
        this.playerList = playerManager;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType gameMode) {
        this.worldData.setGameType(gameMode);
    }

    public ServerConnectionListener getConnection() {
        return this.connection == null ? this.connection = new ServerConnectionListener(this) : this.connection; // Spigot
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean hasGui() {
        return false;
    }

    public boolean publishServer(@Nullable GameType gameMode, boolean cheatsAllowed, int port) {
        return false;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(ServerLevel world, BlockPos pos, Player player) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int playerIdleTimeout) {
        this.playerIdleTimeout = playerIdleTimeout;
    }

    public MinecraftSessionService getSessionService() {
        return this.services.sessionService();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return this.services.profileKeySignatureValidator();
    }

    public GameProfileRepository getProfileRepository() {
        return this.services.profileRepository();
    }

    @Nullable
    public GameProfileCache getProfileCache() {
        return this.services.profileCache();
    }

    @Nullable
    public ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable runnable) {
        if (this.isStopped()) {
            throw new RejectedExecutionException("Server already shutting down");
        } else {
            super.executeIfPossible(runnable);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable ServerLevel world) {
        return world != null ? world.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    // Paper start - Add ServerResourcesReloadedEvent
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public CompletableFuture<Void> reloadResources(Collection<String> dataPacks) {
        return this.reloadResources(dataPacks, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN);
    }
    public CompletableFuture<Void> reloadResources(Collection<String> dataPacks, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
        // Paper end - Add ServerResourcesReloadedEvent
        CompletableFuture<Void> completablefuture = CompletableFuture.supplyAsync(() -> {
            Stream<String> stream = dataPacks.stream(); // CraftBukkit - decompile error
            PackRepository resourcepackrepository = this.packRepository;

            Objects.requireNonNull(this.packRepository);
            return stream.<Pack>map(resourcepackrepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()); // CraftBukkit - decompile error // Paper - decompile error // todo: is this needed anymore?
        }, this).thenCompose((immutablelist) -> {
            MultiPackResourceManager resourcemanager = new MultiPackResourceManager(PackType.SERVER_DATA, immutablelist);

            return ReloadableServerResources.loadResources(resourcemanager, this.registries, this.worldData.enabledFeatures(), this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED, this.getFunctionCompilationLevel(), this.executor, this).whenComplete((datapackresources, throwable) -> {
                if (throwable != null) {
                    resourcemanager.close();
                }

            }).thenApply((datapackresources) -> {
                return new MinecraftServer.ReloadableResources(resourcemanager, datapackresources);
            });
        }).thenAcceptAsync((minecraftserver_reloadableresources) -> {
            this.resources.close();
            this.resources = minecraftserver_reloadableresources;
            this.server.syncCommands(); // SPIGOT-5884: Lost on reload
            this.packRepository.setSelected(dataPacks);
            WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(MinecraftServer.getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures());

            this.worldData.setDataConfiguration(worlddataconfiguration);
            this.resources.managers.updateRegistryTags();
            this.getPlayerList().saveAll();
            this.getPlayerList().reloadResources();
            this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
            this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
            org.bukkit.craftbukkit.block.data.CraftBlockData.reloadCache(); // Paper - cache block data strings; they can be defined by datapacks so refresh it here
            new io.papermc.paper.event.server.ServerResourcesReloadedEvent(cause).callEvent(); // Paper - Add ServerResourcesReloadedEvent; fire after everything has been reloaded
        }, this);

        if (this.isSameThread()) {
            Objects.requireNonNull(completablefuture);
            this.managedBlock(completablefuture::isDone);
        }

        return completablefuture;
    }

    public static WorldDataConfiguration configurePackRepository(PackRepository resourcePackManager, WorldDataConfiguration dataConfiguration, boolean initMode, boolean safeMode) {
        DataPackConfig datapackconfiguration = dataConfiguration.dataPacks();
        FeatureFlagSet featureflagset = initMode ? FeatureFlagSet.of() : dataConfiguration.enabledFeatures();
        FeatureFlagSet featureflagset1 = initMode ? FeatureFlags.REGISTRY.allFlags() : dataConfiguration.enabledFeatures();

        resourcePackManager.reload();
        if (safeMode) {
            return MinecraftServer.configureRepositoryWithSelection(resourcePackManager, List.of("vanilla"), featureflagset, false);
        } else {
            Set<String> set = Sets.newLinkedHashSet();
            Iterator iterator = datapackconfiguration.getEnabled().iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                if (resourcePackManager.isAvailable(s)) {
                    set.add(s);
                } else {
                    MinecraftServer.LOGGER.warn("Missing data pack {}", s);
                }
            }

            iterator = resourcePackManager.getAvailablePacks().iterator();

            while (iterator.hasNext()) {
                Pack resourcepackloader = (Pack) iterator.next();
                String s1 = resourcepackloader.getId();

                if (!datapackconfiguration.getDisabled().contains(s1)) {
                    FeatureFlagSet featureflagset2 = resourcepackloader.getRequestedFeatures();
                    boolean flag2 = set.contains(s1);

                    if (!flag2 && resourcepackloader.getPackSource().shouldAddAutomatically()) {
                        if (featureflagset2.isSubsetOf(featureflagset1)) {
                            MinecraftServer.LOGGER.info("Found new data pack {}, loading it automatically", s1);
                            set.add(s1);
                        } else {
                            MinecraftServer.LOGGER.info("Found new data pack {}, but can't load it due to missing features {}", s1, FeatureFlags.printMissingFlags(featureflagset1, featureflagset2));
                        }
                    }

                    if (flag2 && !featureflagset2.isSubsetOf(featureflagset1)) {
                        MinecraftServer.LOGGER.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.", s1, FeatureFlags.printMissingFlags(featureflagset1, featureflagset2));
                        set.remove(s1);
                    }
                }
            }

            if (set.isEmpty()) {
                MinecraftServer.LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            return MinecraftServer.configureRepositoryWithSelection(resourcePackManager, set, featureflagset, true);
        }
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(PackRepository resourcePackManager, Collection<String> enabledProfiles, FeatureFlagSet enabledFeatures, boolean allowEnabling) {
        resourcePackManager.setSelected(enabledProfiles);
        MinecraftServer.enableForcedFeaturePacks(resourcePackManager, enabledFeatures);
        DataPackConfig datapackconfiguration = MinecraftServer.getSelectedPacks(resourcePackManager, allowEnabling);
        FeatureFlagSet featureflagset1 = resourcePackManager.getRequestedFeatureFlags().join(enabledFeatures);

        return new WorldDataConfiguration(datapackconfiguration, featureflagset1);
    }

    private static void enableForcedFeaturePacks(PackRepository resourcePackManager, FeatureFlagSet enabledFeatures) {
        FeatureFlagSet featureflagset1 = resourcePackManager.getRequestedFeatureFlags();
        FeatureFlagSet featureflagset2 = enabledFeatures.subtract(featureflagset1);

        if (!featureflagset2.isEmpty()) {
            Set<String> set = new ObjectArraySet(resourcePackManager.getSelectedIds());
            Iterator iterator = resourcePackManager.getAvailablePacks().iterator();

            while (iterator.hasNext()) {
                Pack resourcepackloader = (Pack) iterator.next();

                if (featureflagset2.isEmpty()) {
                    break;
                }

                if (resourcepackloader.getPackSource() == PackSource.FEATURE) {
                    String s = resourcepackloader.getId();
                    FeatureFlagSet featureflagset3 = resourcepackloader.getRequestedFeatures();

                    if (!featureflagset3.isEmpty() && featureflagset3.intersects(featureflagset2) && featureflagset3.isSubsetOf(enabledFeatures)) {
                        if (!set.add(s)) {
                            throw new IllegalStateException("Tried to force '" + s + "', but it was already enabled");
                        }

                        MinecraftServer.LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", s);
                        featureflagset2 = featureflagset2.subtract(featureflagset3);
                    }
                }
            }

            resourcePackManager.setSelected(set);
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository dataPackManager, boolean allowEnabling) {
        Collection<String> collection = dataPackManager.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list1 = allowEnabling ? dataPackManager.getAvailableIds().stream().filter((s) -> {
            return !collection.contains(s);
        }).toList() : List.of();

        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers(CommandSourceStack source) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerlist = source.getServer().getPlayerList();
            UserWhiteList whitelist = playerlist.getWhiteList();
            if (!((DedicatedServer) getServer()).getProperties().whiteList.get()) return; // Paper - whitelist not enabled
            List<ServerPlayer> list = Lists.newArrayList(playerlist.getPlayers());
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (!whitelist.isWhiteListed(entityplayer.getGameProfile()) && !this.getPlayerList().isOp(entityplayer.getGameProfile())) { // Paper - Fix kicking ops when whitelist is reloaded (MC-171420)
                    entityplayer.connection.disconnect(net.kyori.adventure.text.Component.text(org.spigotmc.SpigotConfig.whitelistMessage), org.bukkit.event.player.PlayerKickEvent.Cause.WHITELIST); // Paper - use configurable message & kick event cause
                }
            }

        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel worldserver = this.overworld();

        return new CommandSourceStack(this, worldserver == null ? Vec3.ZERO : Vec3.atLowerCornerOf(worldserver.getSharedSpawnPos()), Vec2.ZERO, worldserver, 4, "Server", Component.literal("Server"), this, (Entity) null);
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean enforceWhitelist) {
        this.enforceWhitelist = enforceWhitelist;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        return this.aggregatedTickTimesNanos / (long) Math.min(100, Math.max(this.tickCount, 1));
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public int getProfilePermissions(GameProfile profile) {
        if (this.getPlayerList().isOp(profile)) {
            ServerOpListEntry oplistentry = (ServerOpListEntry) this.getPlayerList().getOps().get(profile);

            return oplistentry != null ? oplistentry.getLevel() : (this.isSingleplayerOwner(profile) ? 4 : (this.isSingleplayer() ? (this.getPlayerList().isAllowCommandsForAllPlayers() ? 4 : 0) : this.getOperatorUserPermissionLevel()));
        } else {
            return 0;
        }
    }

    public ProfilerFiller getProfiler() {
        return this.profiler;
    }

    public abstract boolean isSingleplayerOwner(GameProfile profile);

    public void dumpServerProperties(Path file) throws IOException {}

    private void saveDebugReport(Path path) {
        Path path1 = path.resolve("levels");

        try {
            Iterator iterator = this.levels.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<ResourceKey<Level>, ServerLevel> entry = (Entry) iterator.next();
                ResourceLocation minecraftkey = ((ResourceKey) entry.getKey()).location();
                Path path2 = path1.resolve(minecraftkey.getNamespace()).resolve(minecraftkey.getPath());

                Files.createDirectories(path2);
                ((ServerLevel) entry.getValue()).saveDebugReport(path2);
            }

            this.dumpGameRules(path.resolve("gamerules.txt"));
            this.dumpClasspath(path.resolve("classpath.txt"));
            this.dumpMiscStats(path.resolve("stats.txt"));
            this.dumpThreads(path.resolve("threads.txt"));
            this.dumpServerProperties(path.resolve("server.properties.txt"));
            this.dumpNativeModules(path.resolve("modules.txt"));
        } catch (IOException ioexception) {
            MinecraftServer.LOGGER.warn("Failed to save debug report", ioexception);
        }

    }

    private void dumpMiscStats(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            bufferedwriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            bufferedwriter.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            bufferedwriter.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            bufferedwriter.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    private void dumpGameRules(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            final List<String> list = Lists.newArrayList();
            final GameRules gamerules = this.getGameRules();

            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() { // CraftBukkit - decompile error
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", key.getId(), gamerules.getRule(key)));
                }
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                bufferedwriter.write(s);
            }
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    private void dumpClasspath(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            String s = System.getProperty("java.class.path");
            String s1 = System.getProperty("path.separator");
            Iterator iterator = Splitter.on(s1).split(s).iterator();

            while (iterator.hasNext()) {
                String s2 = (String) iterator.next();

                bufferedwriter.write(s2);
                bufferedwriter.write("\n");
            }
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);

        Arrays.sort(athreadinfo, Comparator.comparing(ThreadInfo::getThreadName));
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            ThreadInfo[] athreadinfo1 = athreadinfo;
            int i = athreadinfo.length;

            for (int j = 0; j < i; ++j) {
                ThreadInfo threadinfo = athreadinfo1[j];

                bufferedwriter.write(threadinfo.toString());
                bufferedwriter.write(10);
            }
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    private void dumpNativeModules(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        label50:
        {
            try {
                label51:
                {
                    ArrayList<NativeModuleLister.NativeModuleInfo> arraylist; // CraftBukkit - decompile error

                    try {
                        arraylist = Lists.newArrayList(NativeModuleLister.listModules());
                    } catch (Throwable throwable) {
                        MinecraftServer.LOGGER.warn("Failed to list native modules", throwable);
                        break label51;
                    }

                    arraylist.sort(Comparator.comparing((nativemodulelister_a) -> {
                        return nativemodulelister_a.name;
                    }));
                    Iterator iterator = arraylist.iterator();

                    while (true) {
                        if (!iterator.hasNext()) {
                            break label50;
                        }

                        NativeModuleLister.NativeModuleInfo nativemodulelister_a = (NativeModuleLister.NativeModuleInfo) iterator.next();

                        bufferedwriter.write(nativemodulelister_a.toString());
                        bufferedwriter.write(10);
                    }
                }
            } catch (Throwable throwable1) {
                if (bufferedwriter != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable1.addSuppressed(throwable2);
                    }
                }

                throw throwable1;
            }

            if (bufferedwriter != null) {
                bufferedwriter.close();
            }

            return;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    // CraftBukkit start
    public boolean isDebugging() {
        return false;
    }

    public static MinecraftServer getServer() {
        return SERVER; // Paper
    }

    @Deprecated
    public static RegistryAccess getDefaultRegistryAccess() {
        return CraftRegistry.getMinecraftRegistry();
    }
    // CraftBukkit end

    private void startMetricsRecordingTick() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()), Util.timeSource, Util.ioPool(), new MetricsPersister("server"), this.onMetricsRecordingStopped, (path) -> {
                this.executeBlocking(() -> {
                    this.saveDebugReport(path.resolve("server"));
                });
                this.onMetricsRecordingFinished.accept(path);
            });
            this.willStartRecordingMetrics = false;
        }

        this.profiler = SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
        this.metricsRecorder.startTick();
        this.profiler.startTick();
    }

    public void endMetricsRecordingTick() {
        this.profiler.endTick();
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> resultConsumer, Consumer<Path> dumpConsumer) {
        this.onMetricsRecordingStopped = (methodprofilerresults) -> {
            this.stopRecordingMetrics();
            resultConsumer.accept(methodprofilerresults);
        };
        this.onMetricsRecordingFinished = dumpConsumer;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
        this.profiler = this.metricsRecorder.getProfiler();
    }

    public Path getWorldPath(LevelResource worldSavePath) {
        return this.storageSource.getLevelPath(worldSavePath);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer player) {
        return (ServerPlayerGameMode) (this.isDemo() ? new DemoMode(player) : new ServerPlayerGameMode(player));
    }

    @Nullable
    public GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        if (this.debugCommandProfiler == null) {
            return EmptyProfileResults.EMPTY;
        } else {
            ProfileResults methodprofilerresults = this.debugCommandProfiler.stop(Util.getNanos(), this.tickCount);

            this.debugCommandProfiler = null;
            return methodprofilerresults;
        }
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component message, ChatType.Bound params, @Nullable String prefix) {
        // Paper start
        net.kyori.adventure.text.Component s1 = io.papermc.paper.adventure.PaperAdventure.asAdventure(params.decorate(message));

        if (prefix != null) {
            MinecraftServer.COMPONENT_LOGGER.info("[{}] {}", prefix, s1);
        } else {
            MinecraftServer.COMPONENT_LOGGER.info("{}", s1);
            // Paper end
        }

    }

    public final java.util.concurrent.ExecutorService chatExecutor = java.util.concurrent.Executors.newCachedThreadPool(
            new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Chat Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER)).build()); // Paper

    public final ChatDecorator improvedChatDecorator = new io.papermc.paper.adventure.ImprovedChatDecorator(this); // Paper - adventure
    public ChatDecorator getChatDecorator() {
        return this.improvedChatDecorator; // Paper - support async chat decoration events
    }

    public boolean logIPs() {
        return true;
    }

    public void subscribeToDebugSample(ServerPlayer player, RemoteDebugSampleType type) {}

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport report, ChunkPos pos, RegionStorageInfo key) {
        Util.ioPool().execute(() -> {
            try {
                Path path = this.getFile("debug");

                FileUtil.createDirectoriesSafe(path);
                String s = FileUtil.sanitizeName(key.level());
                Path path1 = path.resolve("chunk-" + s + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                FileStore filestore = Files.getFileStore(path);
                long i = filestore.getUsableSpace();

                if (i < 8192L) {
                    MinecraftServer.LOGGER.warn("Not storing chunk IO report due to low space on drive {}", filestore.name());
                    return;
                }

                CrashReportCategory crashreportsystemdetails = report.addCategory("Chunk Info");

                Objects.requireNonNull(key);
                crashreportsystemdetails.setDetail("Level", key::level);
                crashreportsystemdetails.setDetail("Dimension", () -> {
                    return key.dimension().location().toString();
                });
                Objects.requireNonNull(key);
                crashreportsystemdetails.setDetail("Storage", key::type);
                Objects.requireNonNull(pos);
                crashreportsystemdetails.setDetail("Position", pos::toString);
                report.saveToFile(path1, ReportType.CHUNK_IO_ERROR);
                MinecraftServer.LOGGER.info("Saved details to {}", report.getSaveFile());
            } catch (Exception exception) {
                MinecraftServer.LOGGER.warn("Failed to store chunk IO exception", exception);
            }

        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable exception, RegionStorageInfo key, ChunkPos chunkPos) {
        MinecraftServer.LOGGER.error("Failed to load chunk {},{}", new Object[]{chunkPos.x, chunkPos.z, exception});
        this.storeChunkIoError(CrashReport.forThrowable(exception, "Chunk load failure"), chunkPos, key);
    }

    @Override
    public void reportChunkSaveFailure(Throwable exception, RegionStorageInfo key, ChunkPos chunkPos) {
        MinecraftServer.LOGGER.error("Failed to save chunk {},{}", new Object[]{chunkPos.x, chunkPos.z, exception});
        this.storeChunkIoError(CrashReport.forThrowable(exception, "Chunk save failure"), chunkPos, key);
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    public static record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {

        public void close() {
            this.resourceManager.close();
        }
    }

    private static class TimeProfiler {

        final long startNanos;
        final int startTick;

        TimeProfiler(long time, int tick) {
            this.startNanos = time;
            this.startTick = tick;
        }

        ProfileResults stop(final long endTime, final int endTick) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String parentPath) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path path) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return endTime;
                }

                @Override
                public int getEndTimeTicks() {
                    return endTick;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }

    public static record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {

    }

    // Paper start - Add tick times API and /mspt command
    public static class TickTimes {
        private final long[] times;

        public TickTimes(int length) {
            times = new long[length];
        }

        void add(int index, long time) {
            times[index % times.length] = time;
        }

        public long[] getTimes() {
            return times.clone();
        }

        public double getAverage() {
            long total = 0L;
            for (long value : times) {
                total += value;
            }
            return ((double) total / (double) times.length) * 1.0E-6D;
        }
    }
    // Paper end - Add tick times API and /mspt command
}
