package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;

public class ThreadedLevelLightEngine extends LevelLightEngine implements AutoCloseable {
    public static final int DEFAULT_BATCH_SIZE = 1000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProcessorMailbox<Runnable> taskMailbox;
    private final ObjectList<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> lightTasks = new ObjectArrayList<>();
    private final ChunkMap chunkMap;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox;
    private final int taskPerBatch = 1000;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ThreadedLevelLightEngine(
        LightChunkGetter chunkProvider,
        ChunkMap chunkLoadingManager,
        boolean hasBlockLight,
        ProcessorMailbox<Runnable> processor,
        ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> executor
    ) {
        super(chunkProvider, true, hasBlockLight);
        this.chunkMap = chunkLoadingManager;
        this.sorterMailbox = executor;
        this.taskMailbox = processor;
    }

    @Override
    public void close() {
    }

    @Override
    public int runLightUpdates() {
        throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Ran automatically on a different thread!"));
    }

    @Override
    public void checkBlock(BlockPos pos) {
        BlockPos blockPos = pos.immutable();
        this.addTask(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()),
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.checkBlock(blockPos), () -> "checkBlock " + blockPos)
        );
    }

    protected void updateChunkStatus(ChunkPos pos) {
        this.addTask(pos.x, pos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.retainData(pos, false);
            super.setLightEnabled(pos, false);

            for (int i = this.getMinLightSection(); i < this.getMaxLightSection(); i++) {
                super.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, i), null);
                super.queueSectionData(LightLayer.SKY, SectionPos.of(pos, i), null);
            }

            for (int j = this.levelHeightAccessor.getMinSection(); j < this.levelHeightAccessor.getMaxSection(); j++) {
                super.updateSectionStatus(SectionPos.of(pos, j), true);
            }
        }, () -> "updateChunkStatus " + pos + " true"));
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean notReady) {
        this.addTask(
            pos.x(),
            pos.z(),
            () -> 0,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.updateSectionStatus(pos, notReady), () -> "updateSectionStatus " + pos + " " + notReady)
        );
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        this.addTask(
            chunkPos.x,
            chunkPos.z,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.propagateLightSources(chunkPos), () -> "propagateLight " + chunkPos)
        );
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean retainData) {
        this.addTask(
            pos.x,
            pos.z,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.setLightEnabled(pos, retainData), () -> "enableLight " + pos + " " + retainData)
        );
    }

    @Override
    public void queueSectionData(LightLayer lightType, SectionPos pos, @Nullable DataLayer nibbles) {
        this.addTask(
            pos.x(),
            pos.z(),
            () -> 0,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.queueSectionData(lightType, pos, nibbles), () -> "queueData " + pos)
        );
    }

    private void addTask(int x, int z, ThreadedLevelLightEngine.TaskType stage, Runnable task) {
        this.addTask(x, z, this.chunkMap.getChunkQueueLevel(ChunkPos.asLong(x, z)), stage, task);
    }

    private void addTask(int x, int z, IntSupplier completedLevelSupplier, ThreadedLevelLightEngine.TaskType stage, Runnable task) {
        this.sorterMailbox.tell(ChunkTaskPriorityQueueSorter.message(() -> {
            this.lightTasks.add(Pair.of(stage, task));
            if (this.lightTasks.size() >= 1000) {
                this.runUpdate();
            }
        }, ChunkPos.asLong(x, z), completedLevelSupplier));
    }

    @Override
    public void retainData(ChunkPos pos, boolean retainData) {
        this.addTask(
            pos.x, pos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> super.retainData(pos, retainData), () -> "retainData " + pos)
        );
    }

    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean bl) {
        ChunkPos chunkPos = chunk.getPos();
        this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            LevelChunkSection[] levelChunkSections = chunk.getSections();

            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection levelChunkSection = levelChunkSections[i];
                if (!levelChunkSection.hasOnlyAir()) {
                    int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                    super.updateSectionStatus(SectionPos.of(chunkPos, j), false);
                }
            }
        }, () -> "initializeLight: " + chunkPos));
        return CompletableFuture.supplyAsync(() -> {
            super.setLightEnabled(chunkPos, bl);
            super.retainData(chunkPos, false);
            return chunk;
        }, task -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, task));
    }

    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean excludeBlocks) {
        ChunkPos chunkPos = chunk.getPos();
        chunk.setLightCorrect(false);
        this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            if (!excludeBlocks) {
                super.propagateLightSources(chunkPos);
            }
        }, () -> "lightChunk " + chunkPos + " " + excludeBlocks));
        return CompletableFuture.supplyAsync(() -> {
            chunk.setLightCorrect(true);
            return chunk;
        }, task -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, task));
    }

    public void tryScheduleUpdate() {
        if ((!this.lightTasks.isEmpty() || super.hasLightWork()) && this.scheduled.compareAndSet(false, true)) {
            this.taskMailbox.tell(() -> {
                this.runUpdate();
                this.scheduled.set(false);
            });
        }
    }

    private void runUpdate() {
        int i = Math.min(this.lightTasks.size(), 1000);
        ObjectListIterator<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> objectListIterator = this.lightTasks.iterator();

        int j;
        for (j = 0; objectListIterator.hasNext() && j < i; j++) {
            Pair<ThreadedLevelLightEngine.TaskType, Runnable> pair = objectListIterator.next();
            if (pair.getFirst() == ThreadedLevelLightEngine.TaskType.PRE_UPDATE) {
                pair.getSecond().run();
            }
        }

        objectListIterator.back(j);
        super.runLightUpdates();

        for (int var5 = 0; objectListIterator.hasNext() && var5 < i; var5++) {
            Pair<ThreadedLevelLightEngine.TaskType, Runnable> pair2 = objectListIterator.next();
            if (pair2.getFirst() == ThreadedLevelLightEngine.TaskType.POST_UPDATE) {
                pair2.getSecond().run();
            }

            objectListIterator.remove();
        }
    }

    public CompletableFuture<?> waitForPendingTasks(int x, int z) {
        return CompletableFuture.runAsync(() -> {
        }, callback -> this.addTask(x, z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, callback));
    }

    static enum TaskType {
        PRE_UPDATE,
        POST_UPDATE;
    }
}
