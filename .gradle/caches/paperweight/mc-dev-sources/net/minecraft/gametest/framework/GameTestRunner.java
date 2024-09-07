package net.minecraft.gametest.framework;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class GameTestRunner {
    public static final int DEFAULT_TESTS_PER_ROW = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    final ServerLevel level;
    private final GameTestTicker testTicker;
    private final List<GameTestInfo> allTestInfos;
    private ImmutableList<GameTestBatch> batches;
    final List<GameTestBatchListener> batchListeners = Lists.newArrayList();
    private final List<GameTestInfo> scheduledForRerun = Lists.newArrayList();
    private final GameTestRunner.GameTestBatcher testBatcher;
    private boolean stopped = true;
    @Nullable
    GameTestBatch currentBatch;
    private final GameTestRunner.StructureSpawner existingStructureSpawner;
    private final GameTestRunner.StructureSpawner newStructureSpawner;
    final boolean haltOnError;

    protected GameTestRunner(
        GameTestRunner.GameTestBatcher batcher,
        Collection<GameTestBatch> batches,
        ServerLevel world,
        GameTestTicker manager,
        GameTestRunner.StructureSpawner reuseSpawner,
        GameTestRunner.StructureSpawner initialSpawner,
        boolean stopAfterFailure
    ) {
        this.level = world;
        this.testTicker = manager;
        this.testBatcher = batcher;
        this.existingStructureSpawner = reuseSpawner;
        this.newStructureSpawner = initialSpawner;
        this.batches = ImmutableList.copyOf(batches);
        this.haltOnError = stopAfterFailure;
        this.allTestInfos = this.batches.stream().flatMap(batch -> batch.gameTestInfos().stream()).collect(Util.toMutableList());
        manager.setRunner(this);
        this.allTestInfos.forEach(state -> state.addListener(new ReportGameListener()));
    }

    public List<GameTestInfo> getTestInfos() {
        return this.allTestInfos;
    }

    public void start() {
        this.stopped = false;
        this.runBatch(0);
    }

    public void stop() {
        this.stopped = true;
        if (this.currentBatch != null) {
            this.currentBatch.afterBatchFunction().accept(this.level);
        }
    }

    public void rerunTest(GameTestInfo state) {
        GameTestInfo gameTestInfo = state.copyReset();
        state.getListeners().forEach(listener -> listener.testAddedForRerun(state, gameTestInfo, this));
        this.allTestInfos.add(gameTestInfo);
        this.scheduledForRerun.add(gameTestInfo);
        if (this.stopped) {
            this.runScheduledRerunTests();
        }
    }

    void runBatch(int batchIndex) {
        if (batchIndex >= this.batches.size()) {
            this.runScheduledRerunTests();
        } else {
            this.currentBatch = this.batches.get(batchIndex);
            this.existingStructureSpawner.onBatchStart(this.level);
            this.newStructureSpawner.onBatchStart(this.level);
            Collection<GameTestInfo> collection = this.createStructuresForBatch(this.currentBatch.gameTestInfos());
            String string = this.currentBatch.name();
            LOGGER.info("Running test batch '{}' ({} tests)...", string, collection.size());
            this.currentBatch.beforeBatchFunction().accept(this.level);
            this.batchListeners.forEach(listener -> listener.testBatchStarting(this.currentBatch));
            final MultipleTestTracker multipleTestTracker = new MultipleTestTracker();
            collection.forEach(multipleTestTracker::addTestToTrack);
            multipleTestTracker.addListener(new GameTestListener() {
                private void testCompleted() {
                    if (multipleTestTracker.isDone()) {
                        GameTestRunner.this.currentBatch.afterBatchFunction().accept(GameTestRunner.this.level);
                        GameTestRunner.this.batchListeners.forEach(listener -> listener.testBatchFinished(GameTestRunner.this.currentBatch));
                        LongSet longSet = new LongArraySet(GameTestRunner.this.level.getForcedChunks());
                        longSet.forEach(chunkPos -> GameTestRunner.this.level.setChunkForced(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), false));
                        GameTestRunner.this.runBatch(batchIndex + 1);
                    }
                }

                @Override
                public void testStructureLoaded(GameTestInfo test) {
                }

                @Override
                public void testPassed(GameTestInfo test, GameTestRunner context) {
                    this.testCompleted();
                }

                @Override
                public void testFailed(GameTestInfo test, GameTestRunner context) {
                    if (GameTestRunner.this.haltOnError) {
                        GameTestRunner.this.currentBatch.afterBatchFunction().accept(GameTestRunner.this.level);
                        LongSet longSet = new LongArraySet(GameTestRunner.this.level.getForcedChunks());
                        longSet.forEach(chunkPos -> GameTestRunner.this.level.setChunkForced(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), false));
                        GameTestTicker.SINGLETON.clear();
                    } else {
                        this.testCompleted();
                    }
                }

                @Override
                public void testAddedForRerun(GameTestInfo prevState, GameTestInfo nextState, GameTestRunner context) {
                }
            });
            collection.forEach(this.testTicker::add);
        }
    }

    private void runScheduledRerunTests() {
        if (!this.scheduledForRerun.isEmpty()) {
            LOGGER.info(
                "Starting re-run of tests: {}",
                this.scheduledForRerun.stream().map(state -> state.getTestFunction().testName()).collect(Collectors.joining(", "))
            );
            this.batches = ImmutableList.copyOf(this.testBatcher.batch(this.scheduledForRerun));
            this.scheduledForRerun.clear();
            this.stopped = false;
            this.runBatch(0);
        } else {
            this.batches = ImmutableList.of();
            this.stopped = true;
        }
    }

    public void addListener(GameTestBatchListener batchListener) {
        this.batchListeners.add(batchListener);
    }

    private Collection<GameTestInfo> createStructuresForBatch(Collection<GameTestInfo> oldStates) {
        return oldStates.stream().map(this::spawn).flatMap(Optional::stream).toList();
    }

    private Optional<GameTestInfo> spawn(GameTestInfo oldState) {
        return oldState.getStructureBlockPos() == null
            ? this.newStructureSpawner.spawnStructure(oldState)
            : this.existingStructureSpawner.spawnStructure(oldState);
    }

    public static void clearMarkers(ServerLevel world) {
        DebugPackets.sendGameTestClearPacket(world);
    }

    public static class Builder {
        private final ServerLevel level;
        private final GameTestTicker testTicker = GameTestTicker.SINGLETON;
        private GameTestRunner.GameTestBatcher batcher = GameTestBatchFactory.fromGameTestInfo();
        private GameTestRunner.StructureSpawner existingStructureSpawner = GameTestRunner.StructureSpawner.IN_PLACE;
        private GameTestRunner.StructureSpawner newStructureSpawner = GameTestRunner.StructureSpawner.NOT_SET;
        private final Collection<GameTestBatch> batches;
        private boolean haltOnError = false;

        private Builder(Collection<GameTestBatch> batches, ServerLevel world) {
            this.batches = batches;
            this.level = world;
        }

        public static GameTestRunner.Builder fromBatches(Collection<GameTestBatch> batches, ServerLevel world) {
            return new GameTestRunner.Builder(batches, world);
        }

        public static GameTestRunner.Builder fromInfo(Collection<GameTestInfo> states, ServerLevel world) {
            return fromBatches(GameTestBatchFactory.fromGameTestInfo().batch(states), world);
        }

        public GameTestRunner.Builder haltOnError(boolean stopAfterFailure) {
            this.haltOnError = stopAfterFailure;
            return this;
        }

        public GameTestRunner.Builder newStructureSpawner(GameTestRunner.StructureSpawner initialSpawner) {
            this.newStructureSpawner = initialSpawner;
            return this;
        }

        public GameTestRunner.Builder existingStructureSpawner(StructureGridSpawner reuseSpawner) {
            this.existingStructureSpawner = reuseSpawner;
            return this;
        }

        public GameTestRunner.Builder batcher(GameTestRunner.GameTestBatcher batcher) {
            this.batcher = batcher;
            return this;
        }

        public GameTestRunner build() {
            return new GameTestRunner(
                this.batcher, this.batches, this.level, this.testTicker, this.existingStructureSpawner, this.newStructureSpawner, this.haltOnError
            );
        }
    }

    public interface GameTestBatcher {
        Collection<GameTestBatch> batch(Collection<GameTestInfo> states);
    }

    public interface StructureSpawner {
        GameTestRunner.StructureSpawner IN_PLACE = oldState -> Optional.of(oldState.prepareTestStructure().placeStructure().startExecution(1));
        GameTestRunner.StructureSpawner NOT_SET = oldState -> Optional.empty();

        Optional<GameTestInfo> spawnStructure(GameTestInfo oldState);

        default void onBatchStart(ServerLevel world) {
        }
    }
}
