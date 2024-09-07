package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.slf4j.Logger;

public class GameTestServer extends MinecraftServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_REPORT_INTERVAL = 20;
    private static final int TEST_POSITION_RANGE = 14999992;
    private static final Services NO_SERVICES = new Services(null, ServicesKeySet.EMPTY, null, null);
    private final LocalSampleLogger sampleLogger = new LocalSampleLogger(4);
    private List<GameTestBatch> testBatches = new ArrayList<>();
    private final List<TestFunction> testFunctions;
    private final BlockPos spawnPos;
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private static final GameRules TEST_GAME_RULES = Util.make(new GameRules(), gameRules -> {
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        gameRules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        gameRules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
    });
    private static final WorldOptions WORLD_OPTIONS = new WorldOptions(0L, false, false);
    @Nullable
    private MultipleTestTracker testTracker;

    public static GameTestServer create(
        Thread thread, LevelStorageSource.LevelStorageAccess session, PackRepository resourcePackManager, Collection<TestFunction> batches, BlockPos pos
    ) {
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("No test functions were given!");
        } else {
            resourcePackManager.reload();
            WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(
                new DataPackConfig(new ArrayList<>(resourcePackManager.getAvailableIds()), List.of()), FeatureFlags.REGISTRY.allFlags()
            );
            LevelSettings levelSettings = new LevelSettings(
                "Test Level", GameType.CREATIVE, false, Difficulty.NORMAL, true, TEST_GAME_RULES, worldDataConfiguration
            );
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(resourcePackManager, worldDataConfiguration, false, true);
            WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, 4);

            try {
                LOGGER.debug("Starting resource loading");
                Stopwatch stopwatch = Stopwatch.createStarted();
                WorldStem worldStem = Util.<WorldStem>blockUntilDone(
                        executor -> WorldLoader.load(
                                initConfig,
                                context -> {
                                    Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
                                    WorldDimensions.Complete complete = context.datapackWorldgen()
                                        .registryOrThrow(Registries.WORLD_PRESET)
                                        .getHolderOrThrow(WorldPresets.FLAT)
                                        .value()
                                        .createWorldDimensions()
                                        .bake(registry);
                                    return new WorldLoader.DataLoadOutput<>(
                                        new PrimaryLevelData(levelSettings, WORLD_OPTIONS, complete.specialWorldProperty(), complete.lifecycle()),
                                        complete.dimensionsRegistryAccess()
                                    );
                                },
                                WorldStem::new,
                                Util.backgroundExecutor(),
                                executor
                            )
                    )
                    .get();
                stopwatch.stop();
                LOGGER.debug("Finished resource loading after {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
                return new GameTestServer(thread, session, resourcePackManager, worldStem, batches, pos);
            } catch (Exception var11) {
                LOGGER.warn("Failed to load vanilla datapack, bit oops", (Throwable)var11);
                System.exit(-1);
                throw new IllegalStateException();
            }
        }
    }

    private GameTestServer(
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess session,
        PackRepository dataPackManager,
        WorldStem saveLoader,
        Collection<TestFunction> testFunctions,
        BlockPos pos
    ) {
        super(
            serverThread,
            session,
            dataPackManager,
            saveLoader,
            Proxy.NO_PROXY,
            DataFixers.getDataFixer(),
            NO_SERVICES,
            LoggerChunkProgressListener::createFromGameruleRadius
        );
        this.testFunctions = Lists.newArrayList(testFunctions);
        this.spawnPos = pos;
    }

    @Override
    public boolean initServer() {
        this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, 1) {
        });
        this.loadLevel();
        ServerLevel serverLevel = this.overworld();
        this.testBatches = Lists.newArrayList(GameTestBatchFactory.fromTestFunction(this.testFunctions, serverLevel));
        serverLevel.setDefaultSpawnPos(this.spawnPos, 0.0F);
        int i = 20000000;
        serverLevel.setWeatherParameters(20000000, 20000000, false, false);
        LOGGER.info("Started game test server");
        return true;
    }

    @Override
    public void tickServer(BooleanSupplier shouldKeepTicking) {
        super.tickServer(shouldKeepTicking);
        ServerLevel serverLevel = this.overworld();
        if (!this.haveTestsStarted()) {
            this.startTests(serverLevel);
        }

        if (serverLevel.getGameTime() % 20L == 0L) {
            LOGGER.info(this.testTracker.getProgressBar());
        }

        if (this.testTracker.isDone()) {
            this.halt(false);
            LOGGER.info(this.testTracker.getProgressBar());
            GlobalTestReporter.finish();
            LOGGER.info("========= {} GAME TESTS COMPLETE IN {} ======================", this.testTracker.getTotalCount(), this.stopwatch.stop());
            if (this.testTracker.hasFailedRequired()) {
                LOGGER.info("{} required tests failed :(", this.testTracker.getFailedRequiredCount());
                this.testTracker.getFailedRequired().forEach(test -> LOGGER.info("   - {}", test.getTestName()));
            } else {
                LOGGER.info("All {} required tests passed :)", this.testTracker.getTotalCount());
            }

            if (this.testTracker.hasFailedOptional()) {
                LOGGER.info("{} optional tests failed", this.testTracker.getFailedOptionalCount());
                this.testTracker.getFailedOptional().forEach(test -> LOGGER.info("   - {}", test.getTestName()));
            }

            LOGGER.info("====================================================");
        }
    }

    @Override
    public SampleLogger getTickTimeLogger() {
        return this.sampleLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return false;
    }

    @Override
    public void waitUntilNextTick() {
        this.runAllTasks();
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport details) {
        details.setDetail("Type", "Game test server");
        return details;
    }

    @Override
    public void onServerExit() {
        super.onServerExit();
        LOGGER.info("Game test server shutting down");
        System.exit(this.testTracker.getFailedRequiredCount());
    }

    @Override
    public void onServerCrash(CrashReport report) {
        super.onServerCrash(report);
        LOGGER.error("Game test server crashed\n{}", report.getFriendlyReport(ReportType.CRASH));
        System.exit(1);
    }

    private void startTests(ServerLevel world) {
        BlockPos blockPos = new BlockPos(
            world.random.nextIntBetweenInclusive(-14999992, 14999992), -59, world.random.nextIntBetweenInclusive(-14999992, 14999992)
        );
        GameTestRunner gameTestRunner = GameTestRunner.Builder.fromBatches(this.testBatches, world)
            .newStructureSpawner(new StructureGridSpawner(blockPos, 8, false))
            .build();
        Collection<GameTestInfo> collection = gameTestRunner.getTestInfos();
        this.testTracker = new MultipleTestTracker(collection);
        LOGGER.info("{} tests are now running at position {}!", this.testTracker.getTotalCount(), blockPos.toShortString());
        this.stopwatch.reset();
        this.stopwatch.start();
        gameTestRunner.start();
    }

    private boolean haveTestsStarted() {
        return this.testTracker != null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public int getOperatorUserPermissionLevel() {
        return 0;
    }

    @Override
    public int getFunctionCompilationLevel() {
        return 4;
    }

    @Override
    public boolean shouldRconBroadcast() {
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        return false;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return 0;
    }

    @Override
    public boolean isEpollEnabled() {
        return false;
    }

    @Override
    public boolean isCommandBlockEnabled() {
        return true;
    }

    @Override
    public boolean isPublished() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile profile) {
        return false;
    }
}
