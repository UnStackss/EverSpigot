package net.minecraft.world.level.chunk.status;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

public record ChunkStep(
    ChunkStatus targetStatus, ChunkDependencies directDependencies, ChunkDependencies accumulatedDependencies, int blockStateWriteRadius, ChunkStatusTask task
) {
    public int getAccumulatedRadiusOf(ChunkStatus status) {
        return status == this.targetStatus ? 0 : this.accumulatedDependencies.getRadiusOf(status);
    }

    public CompletableFuture<ChunkAccess> apply(WorldGenContext context, StaticCache2D<GenerationChunkHolder> staticCache2D, ChunkAccess chunk) {
        if (chunk.getPersistedStatus().isBefore(this.targetStatus)) {
            ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onChunkGenerate(chunk.getPos(), context.level().dimension(), this.targetStatus.getName());
            return this.task.doWork(context, this, staticCache2D, chunk).thenApply(generated -> this.completeChunkGeneration(generated, profiledDuration));
        } else {
            return this.task.doWork(context, this, staticCache2D, chunk);
        }
    }

    private ChunkAccess completeChunkGeneration(ChunkAccess chunk, @Nullable ProfiledDuration finishCallback) {
        if (chunk instanceof ProtoChunk protoChunk && protoChunk.getPersistedStatus().isBefore(this.targetStatus)) {
            protoChunk.setPersistedStatus(this.targetStatus);
        }

        if (finishCallback != null) {
            finishCallback.finish();
        }

        return chunk;
    }

    public static class Builder {
        private final ChunkStatus status;
        @Nullable
        private final ChunkStep parent;
        private ChunkStatus[] directDependenciesByRadius;
        private int blockStateWriteRadius = -1;
        private ChunkStatusTask task = ChunkStatusTasks::passThrough;

        protected Builder(ChunkStatus targetStatus) {
            if (targetStatus.getParent() != targetStatus) {
                throw new IllegalArgumentException("Not starting with the first status: " + targetStatus);
            } else {
                this.status = targetStatus;
                this.parent = null;
                this.directDependenciesByRadius = new ChunkStatus[0];
            }
        }

        protected Builder(ChunkStatus blockStateWriteRadius, ChunkStep previousStep) {
            if (previousStep.targetStatus.getIndex() != blockStateWriteRadius.getIndex() - 1) {
                throw new IllegalArgumentException("Out of order status: " + blockStateWriteRadius);
            } else {
                this.status = blockStateWriteRadius;
                this.parent = previousStep;
                this.directDependenciesByRadius = new ChunkStatus[]{previousStep.targetStatus};
            }
        }

        public ChunkStep.Builder addRequirement(ChunkStatus status, int level) {
            if (status.isOrAfter(this.status)) {
                throw new IllegalArgumentException("Status " + status + " can not be required by " + this.status);
            } else {
                ChunkStatus[] chunkStatuss = this.directDependenciesByRadius;
                int i = level + 1;
                if (i > chunkStatuss.length) {
                    this.directDependenciesByRadius = new ChunkStatus[i];
                    Arrays.fill(this.directDependenciesByRadius, status);
                }

                for (int j = 0; j < Math.min(i, chunkStatuss.length); j++) {
                    this.directDependenciesByRadius[j] = ChunkStatus.max(chunkStatuss[j], status);
                }

                return this;
            }
        }

        public ChunkStep.Builder blockStateWriteRadius(int blockStateWriteRadius) {
            this.blockStateWriteRadius = blockStateWriteRadius;
            return this;
        }

        public ChunkStep.Builder setTask(ChunkStatusTask task) {
            this.task = task;
            return this;
        }

        public ChunkStep build() {
            return new ChunkStep(
                this.status,
                new ChunkDependencies(ImmutableList.copyOf(this.directDependenciesByRadius)),
                new ChunkDependencies(ImmutableList.copyOf(this.buildAccumulatedDependencies())),
                this.blockStateWriteRadius,
                this.task
            );
        }

        private ChunkStatus[] buildAccumulatedDependencies() {
            if (this.parent == null) {
                return this.directDependenciesByRadius;
            } else {
                int i = this.getRadiusOfParent(this.parent.targetStatus);
                ChunkDependencies chunkDependencies = this.parent.accumulatedDependencies;
                ChunkStatus[] chunkStatuss = new ChunkStatus[Math.max(i + chunkDependencies.size(), this.directDependenciesByRadius.length)];

                for (int j = 0; j < chunkStatuss.length; j++) {
                    int k = j - i;
                    if (k < 0 || k >= chunkDependencies.size()) {
                        chunkStatuss[j] = this.directDependenciesByRadius[j];
                    } else if (j >= this.directDependenciesByRadius.length) {
                        chunkStatuss[j] = chunkDependencies.get(k);
                    } else {
                        chunkStatuss[j] = ChunkStatus.max(this.directDependenciesByRadius[j], chunkDependencies.get(k));
                    }
                }

                return chunkStatuss;
            }
        }

        private int getRadiusOfParent(ChunkStatus status) {
            for (int i = this.directDependenciesByRadius.length - 1; i >= 0; i--) {
                if (this.directDependenciesByRadius[i].isOrAfter(status)) {
                    return i;
                }
            }

            return 0;
        }
    }
}
