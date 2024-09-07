package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkGenerationTask {
    private final GeneratingChunkMap chunkMap;
    private final ChunkPos pos;
    @Nullable
    private ChunkStatus scheduledStatus = null;
    public final ChunkStatus targetStatus;
    private volatile boolean markedForCancellation;
    private final List<CompletableFuture<ChunkResult<ChunkAccess>>> scheduledLayer = new ArrayList<>();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private boolean needsGeneration;

    private ChunkGenerationTask(GeneratingChunkMap chunkLoadingManager, ChunkStatus targetStatus, ChunkPos pos, StaticCache2D<GenerationChunkHolder> chunks) {
        this.chunkMap = chunkLoadingManager;
        this.targetStatus = targetStatus;
        this.pos = pos;
        this.cache = chunks;
    }

    public static ChunkGenerationTask create(GeneratingChunkMap chunkLoadingManager, ChunkStatus targetStatus, ChunkPos pos) {
        int i = ChunkPyramid.GENERATION_PYRAMID.getStepTo(targetStatus).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
        StaticCache2D<GenerationChunkHolder> staticCache2D = StaticCache2D.create(
            pos.x, pos.z, i, (x, z) -> chunkLoadingManager.acquireGeneration(ChunkPos.asLong(x, z))
        );
        return new ChunkGenerationTask(chunkLoadingManager, targetStatus, pos, staticCache2D);
    }

    @Nullable
    public CompletableFuture<?> runUntilWait() {
        while (true) {
            CompletableFuture<?> completableFuture = this.waitForScheduledLayer();
            if (completableFuture != null) {
                return completableFuture;
            }

            if (this.markedForCancellation || this.scheduledStatus == this.targetStatus) {
                this.releaseClaim();
                return null;
            }

            this.scheduleNextLayer();
        }
    }

    private void scheduleNextLayer() {
        ChunkStatus chunkStatus;
        if (this.scheduledStatus == null) {
            chunkStatus = ChunkStatus.EMPTY;
        } else if (!this.needsGeneration && this.scheduledStatus == ChunkStatus.EMPTY && !this.canLoadWithoutGeneration()) {
            this.needsGeneration = true;
            chunkStatus = ChunkStatus.EMPTY;
        } else {
            chunkStatus = ChunkStatus.getStatusList().get(this.scheduledStatus.getIndex() + 1);
        }

        this.scheduleLayer(chunkStatus, this.needsGeneration);
        this.scheduledStatus = chunkStatus;
    }

    public void markForCancellation() {
        this.markedForCancellation = true;
    }

    private void releaseClaim() {
        GenerationChunkHolder generationChunkHolder = this.cache.get(this.pos.x, this.pos.z);
        generationChunkHolder.removeTask(this);
        this.cache.forEach(this.chunkMap::releaseGeneration);
    }

    private boolean canLoadWithoutGeneration() {
        if (this.targetStatus == ChunkStatus.EMPTY) {
            return true;
        } else {
            ChunkStatus chunkStatus = this.cache.get(this.pos.x, this.pos.z).getPersistedStatus();
            if (chunkStatus != null && !chunkStatus.isBefore(this.targetStatus)) {
                ChunkDependencies chunkDependencies = ChunkPyramid.LOADING_PYRAMID.getStepTo(this.targetStatus).accumulatedDependencies();
                int i = chunkDependencies.getRadius();

                for (int j = this.pos.x - i; j <= this.pos.x + i; j++) {
                    for (int k = this.pos.z - i; k <= this.pos.z + i; k++) {
                        int l = this.pos.getChessboardDistance(j, k);
                        ChunkStatus chunkStatus2 = chunkDependencies.get(l);
                        ChunkStatus chunkStatus3 = this.cache.get(j, k).getPersistedStatus();
                        if (chunkStatus3 == null || chunkStatus3.isBefore(chunkStatus2)) {
                            return false;
                        }
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public GenerationChunkHolder getCenter() {
        return this.cache.get(this.pos.x, this.pos.z);
    }

    private void scheduleLayer(ChunkStatus targetStatus, boolean allowGeneration) {
        int i = this.getRadiusForLayer(targetStatus, allowGeneration);

        for (int j = this.pos.x - i; j <= this.pos.x + i; j++) {
            for (int k = this.pos.z - i; k <= this.pos.z + i; k++) {
                GenerationChunkHolder generationChunkHolder = this.cache.get(j, k);
                if (this.markedForCancellation || !this.scheduleChunkInLayer(targetStatus, allowGeneration, generationChunkHolder)) {
                    return;
                }
            }
        }
    }

    private int getRadiusForLayer(ChunkStatus status, boolean generate) {
        ChunkPyramid chunkPyramid = generate ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
        return chunkPyramid.getStepTo(this.targetStatus).getAccumulatedRadiusOf(status);
    }

    private boolean scheduleChunkInLayer(ChunkStatus targetStatus, boolean allowGeneration, GenerationChunkHolder chunkHolder) {
        ChunkStatus chunkStatus = chunkHolder.getPersistedStatus();
        boolean bl = chunkStatus != null && targetStatus.isAfter(chunkStatus);
        ChunkPyramid chunkPyramid = bl ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
        if (bl && !allowGeneration) {
            throw new IllegalStateException("Can't load chunk, but didn't expect to need to generate");
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = chunkHolder.applyStep(
                chunkPyramid.getStepTo(targetStatus), this.chunkMap, this.cache
            );
            ChunkResult<ChunkAccess> chunkResult = completableFuture.getNow(null);
            if (chunkResult == null) {
                this.scheduledLayer.add(completableFuture);
                return true;
            } else if (chunkResult.isSuccess()) {
                return true;
            } else {
                this.markForCancellation();
                return false;
            }
        }
    }

    @Nullable
    private CompletableFuture<?> waitForScheduledLayer() {
        while (!this.scheduledLayer.isEmpty()) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.scheduledLayer.getLast();
            ChunkResult<ChunkAccess> chunkResult = completableFuture.getNow(null);
            if (chunkResult == null) {
                return completableFuture;
            }

            this.scheduledLayer.removeLast();
            if (!chunkResult.isSuccess()) {
                this.markForCancellation();
            }
        }

        return null;
    }
}
