package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public abstract class GenerationChunkHolder {
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private static final ChunkResult<ChunkAccess> NOT_DONE_YET = ChunkResult.error("Not done yet");
    public static final ChunkResult<ChunkAccess> UNLOADED_CHUNK = ChunkResult.error("Unloaded chunk");
    public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_CHUNK);
    protected final ChunkPos pos;
    @Nullable
    private volatile ChunkStatus highestAllowedStatus;
    private final AtomicReference<ChunkStatus> startedWork = new AtomicReference<>();
    private final AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new AtomicReferenceArray<>(CHUNK_STATUSES.size());
    private final AtomicReference<ChunkGenerationTask> task = new AtomicReference<>();
    private final AtomicInteger generationRefCount = new AtomicInteger();

    public GenerationChunkHolder(ChunkPos pos) {
        this.pos = pos;
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus requestedStatus, ChunkMap chunkLoadingManager) {
        if (this.isStatusDisallowed(requestedStatus)) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.getOrCreateFuture(requestedStatus);
            if (completableFuture.isDone()) {
                return completableFuture;
            } else {
                ChunkGenerationTask chunkGenerationTask = this.task.get();
                if (chunkGenerationTask == null || requestedStatus.isAfter(chunkGenerationTask.targetStatus)) {
                    this.rescheduleChunkTask(chunkLoadingManager, requestedStatus);
                }

                return completableFuture;
            }
        }
    }

    CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step, GeneratingChunkMap chunkLoadingManager, StaticCache2D<GenerationChunkHolder> chunks) {
        if (this.isStatusDisallowed(step.targetStatus())) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            return this.acquireStatusBump(step.targetStatus()) ? chunkLoadingManager.applyStep(this, step, chunks).handle((chunk, throwable) -> {
                if (throwable != null) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception chunk generation/loading");
                    MinecraftServer.setFatalException(new ReportedException(crashReport));
                } else {
                    this.completeFuture(step.targetStatus(), chunk);
                }

                return ChunkResult.of(chunk);
            }) : this.getOrCreateFuture(step.targetStatus());
        }
    }

    protected void updateHighestAllowedStatus(ChunkMap chunkLoadingManager) {
        ChunkStatus chunkStatus = this.highestAllowedStatus;
        ChunkStatus chunkStatus2 = ChunkLevel.generationStatus(this.getTicketLevel());
        this.highestAllowedStatus = chunkStatus2;
        boolean bl = chunkStatus != null && (chunkStatus2 == null || chunkStatus2.isBefore(chunkStatus));
        if (bl) {
            this.failAndClearPendingFuturesBetween(chunkStatus2, chunkStatus);
            if (this.task.get() != null) {
                this.rescheduleChunkTask(chunkLoadingManager, this.findHighestStatusWithPendingFuture(chunkStatus2));
            }
        }
    }

    public void replaceProtoChunk(ImposterProtoChunk chunk) {
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));

        for (int i = 0; i < this.futures.length() - 1; i++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture2 = this.futures.get(i);
            Objects.requireNonNull(completableFuture2);
            ChunkAccess chunkAccess = completableFuture2.getNow(NOT_DONE_YET).orElse(null);
            if (!(chunkAccess instanceof ProtoChunk)) {
                throw new IllegalStateException("Trying to replace a ProtoChunk, but found " + chunkAccess);
            }

            if (!this.futures.compareAndSet(i, completableFuture2, completableFuture)) {
                throw new IllegalStateException("Future changed by other thread while trying to replace it");
            }
        }
    }

    void removeTask(ChunkGenerationTask loader) {
        this.task.compareAndSet(loader, null);
    }

    private void rescheduleChunkTask(ChunkMap chunkLoadingManager, @Nullable ChunkStatus requestedStatus) {
        ChunkGenerationTask chunkGenerationTask;
        if (requestedStatus != null) {
            chunkGenerationTask = chunkLoadingManager.scheduleGenerationTask(requestedStatus, this.getPos());
        } else {
            chunkGenerationTask = null;
        }

        ChunkGenerationTask chunkGenerationTask3 = this.task.getAndSet(chunkGenerationTask);
        if (chunkGenerationTask3 != null) {
            chunkGenerationTask3.markForCancellation();
        }
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus status) {
        if (this.isStatusDisallowed(status)) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            int i = status.getIndex();
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(i);

            while (completableFuture == null) {
                CompletableFuture<ChunkResult<ChunkAccess>> completableFuture2 = new CompletableFuture<>();
                completableFuture = this.futures.compareAndExchange(i, null, completableFuture2);
                if (completableFuture == null) {
                    if (this.isStatusDisallowed(status)) {
                        this.failAndClearPendingFuture(i, completableFuture2);
                        return UNLOADED_CHUNK_FUTURE;
                    }

                    return completableFuture2;
                }
            }

            return completableFuture;
        }
    }

    private void failAndClearPendingFuturesBetween(@Nullable ChunkStatus from, ChunkStatus to) {
        int i = from == null ? 0 : from.getIndex() + 1;
        int j = to.getIndex();

        for (int k = i; k <= j; k++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(k);
            if (completableFuture != null) {
                this.failAndClearPendingFuture(k, completableFuture);
            }
        }
    }

    private void failAndClearPendingFuture(int statusIndex, CompletableFuture<ChunkResult<ChunkAccess>> previousFuture) {
        if (previousFuture.complete(UNLOADED_CHUNK) && !this.futures.compareAndSet(statusIndex, previousFuture, null)) {
            throw new IllegalStateException("Nothing else should replace the future here");
        }
    }

    private void completeFuture(ChunkStatus status, ChunkAccess chunk) {
        ChunkResult<ChunkAccess> chunkResult = ChunkResult.of(chunk);
        int i = status.getIndex();

        while (true) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(i);
            if (completableFuture == null) {
                if (this.futures.compareAndSet(i, null, CompletableFuture.completedFuture(chunkResult))) {
                    return;
                }
            } else {
                if (completableFuture.complete(chunkResult)) {
                    return;
                }

                if (completableFuture.getNow(NOT_DONE_YET).isSuccess()) {
                    throw new IllegalStateException("Trying to complete a future but found it to be completed successfully already");
                }

                Thread.yield();
            }
        }
    }

    @Nullable
    private ChunkStatus findHighestStatusWithPendingFuture(@Nullable ChunkStatus checkUpperBound) {
        if (checkUpperBound == null) {
            return null;
        } else {
            ChunkStatus chunkStatus = checkUpperBound;

            for (ChunkStatus chunkStatus2 = this.startedWork.get();
                chunkStatus2 == null || chunkStatus.isAfter(chunkStatus2);
                chunkStatus = chunkStatus.getParent()
            ) {
                if (this.futures.get(chunkStatus.getIndex()) != null) {
                    return chunkStatus;
                }

                if (chunkStatus == ChunkStatus.EMPTY) {
                    break;
                }
            }

            return null;
        }
    }

    private boolean acquireStatusBump(ChunkStatus nextStatus) {
        ChunkStatus chunkStatus = nextStatus == ChunkStatus.EMPTY ? null : nextStatus.getParent();
        ChunkStatus chunkStatus2 = this.startedWork.compareAndExchange(chunkStatus, nextStatus);
        if (chunkStatus2 == chunkStatus) {
            return true;
        } else if (chunkStatus2 != null && !nextStatus.isAfter(chunkStatus2)) {
            return false;
        } else {
            throw new IllegalStateException("Unexpected last startedWork status: " + chunkStatus2 + " while trying to start: " + nextStatus);
        }
    }

    private boolean isStatusDisallowed(ChunkStatus status) {
        ChunkStatus chunkStatus = this.highestAllowedStatus;
        return chunkStatus == null || status.isAfter(chunkStatus);
    }

    public void increaseGenerationRefCount() {
        this.generationRefCount.incrementAndGet();
    }

    public void decreaseGenerationRefCount() {
        int i = this.generationRefCount.decrementAndGet();
        if (i < 0) {
            throw new IllegalStateException("More releases than claims. Count: " + i);
        }
    }

    public int getGenerationRefCount() {
        return this.generationRefCount.get();
    }

    @Nullable
    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus requestedStatus) {
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(requestedStatus.getIndex());
        return completableFuture == null ? null : completableFuture.getNow(NOT_DONE_YET).orElse(null);
    }

    @Nullable
    public ChunkAccess getChunkIfPresent(ChunkStatus requestedStatus) {
        return this.isStatusDisallowed(requestedStatus) ? null : this.getChunkIfPresentUnchecked(requestedStatus);
    }

    @Nullable
    public ChunkAccess getLatestChunk() {
        ChunkStatus chunkStatus = this.startedWork.get();
        if (chunkStatus == null) {
            return null;
        } else {
            ChunkAccess chunkAccess = this.getChunkIfPresentUnchecked(chunkStatus);
            return chunkAccess != null ? chunkAccess : this.getChunkIfPresentUnchecked(chunkStatus.getParent());
        }
    }

    @Nullable
    public ChunkStatus getPersistedStatus() {
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(ChunkStatus.EMPTY.getIndex());
        ChunkAccess chunkAccess = completableFuture == null ? null : completableFuture.getNow(NOT_DONE_YET).orElse(null);
        return chunkAccess == null ? null : chunkAccess.getPersistedStatus();
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public FullChunkStatus getFullStatus() {
        return ChunkLevel.fullStatus(this.getTicketLevel());
    }

    public abstract int getTicketLevel();

    public abstract int getQueueLevel();

    @VisibleForDebug
    public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> list = new ArrayList<>();

        for (int i = 0; i < CHUNK_STATUSES.size(); i++) {
            list.add(Pair.of(CHUNK_STATUSES.get(i), this.futures.get(i)));
        }

        return list;
    }

    @Nullable
    @VisibleForDebug
    public ChunkStatus getLatestStatus() {
        for (int i = CHUNK_STATUSES.size() - 1; i >= 0; i--) {
            ChunkStatus chunkStatus = CHUNK_STATUSES.get(i);
            ChunkAccess chunkAccess = this.getChunkIfPresentUnchecked(chunkStatus);
            if (chunkAccess != null) {
                return chunkStatus;
            }
        }

        return null;
    }
}
