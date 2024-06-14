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
    // Paper - rewrite chunk system

    public GenerationChunkHolder(ChunkPos pos) {
        this.pos = pos;
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus requestedStatus, ChunkMap chunkLoadingManager) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step, GeneratingChunkMap chunkLoadingManager, StaticCache2D<GenerationChunkHolder> chunks) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected void updateHighestAllowedStatus(ChunkMap chunkLoadingManager) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void replaceProtoChunk(ImposterProtoChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    void removeTask(ChunkGenerationTask loader) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void rescheduleChunkTask(ChunkMap chunkLoadingManager, @Nullable ChunkStatus requestedStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus status) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void failAndClearPendingFuturesBetween(@Nullable ChunkStatus from, ChunkStatus to) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void failAndClearPendingFuture(int statusIndex, CompletableFuture<ChunkResult<ChunkAccess>> previousFuture) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void completeFuture(ChunkStatus status, ChunkAccess chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    private ChunkStatus findHighestStatusWithPendingFuture(@Nullable ChunkStatus checkUpperBound) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private boolean acquireStatusBump(ChunkStatus nextStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private boolean isStatusDisallowed(ChunkStatus status) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void increaseGenerationRefCount() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void decreaseGenerationRefCount() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public int getGenerationRefCount() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus requestedStatus) {
        // Paper start - rewrite chunk system
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getChunkIfPresentUnchecked(requestedStatus);
        // Paper end - rewrite chunk system
    }

    @Nullable
    public ChunkAccess getChunkIfPresent(ChunkStatus requestedStatus) {
        // Paper start - rewrite chunk system
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getChunkIfPresent(requestedStatus);
        // Paper end - rewrite chunk system
    }

    @Nullable
    public ChunkAccess getLatestChunk() {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder.ChunkCompletion lastCompletion = ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.chunk();
        // Paper end - rewrite chunk system
    }

    @Nullable
    public ChunkStatus getPersistedStatus() {
        // Paper start - rewrite chunk system
        final ChunkAccess chunk = this.getLatestChunk();
        return chunk == null ? null : chunk.getPersistedStatus();
        // Paper end - rewrite chunk system
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public FullChunkStatus getFullStatus() {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getChunkStatus(); // Paper - rewrite chunk system
    }

    public abstract int getTicketLevel();

    public abstract int getQueueLevel();

    @VisibleForDebug
    public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        throw new UnsupportedOperationException();  // Paper - rewrite chunk system
    }

    @Nullable
    @VisibleForDebug
    public ChunkStatus getLatestStatus() {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder.ChunkCompletion lastCompletion = ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.genStatus();
        // Paper end - rewrite chunk system
    }
}
