package net.minecraft.server.level;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jetbrains.annotations.Contract;

public class ChunkLevel {
    public static final int FULL_CHUNK_LEVEL = 33;
    public static final int BLOCK_TICKING_LEVEL = 32;
    public static final int ENTITY_TICKING_LEVEL = 31;
    private static final ChunkStep FULL_CHUNK_STEP = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL);
    public static final int RADIUS_AROUND_FULL_CHUNK = FULL_CHUNK_STEP.accumulatedDependencies().getRadius();
    public static final int MAX_LEVEL = 33 + RADIUS_AROUND_FULL_CHUNK;

    @Nullable
    public static ChunkStatus generationStatus(int level) {
        return getStatusAroundFullChunk(level - 33, null);
    }

    @Nullable
    @Contract("_,!null->!null;_,_->_")
    public static ChunkStatus getStatusAroundFullChunk(int additionalLevel, @Nullable ChunkStatus emptyStatus) {
        if (additionalLevel > RADIUS_AROUND_FULL_CHUNK) {
            return emptyStatus;
        } else {
            return additionalLevel <= 0 ? ChunkStatus.FULL : FULL_CHUNK_STEP.accumulatedDependencies().get(additionalLevel);
        }
    }

    public static ChunkStatus getStatusAroundFullChunk(int level) {
        return getStatusAroundFullChunk(level, ChunkStatus.EMPTY);
    }

    public static int byStatus(ChunkStatus status) {
        return 33 + FULL_CHUNK_STEP.getAccumulatedRadiusOf(status);
    }

    public static FullChunkStatus fullStatus(int level) {
        if (level <= 31) {
            return FullChunkStatus.ENTITY_TICKING;
        } else if (level <= 32) {
            return FullChunkStatus.BLOCK_TICKING;
        } else {
            return level <= 33 ? FullChunkStatus.FULL : FullChunkStatus.INACCESSIBLE;
        }
    }

    public static int byStatus(FullChunkStatus type) {
        return switch (type) {
            case INACCESSIBLE -> MAX_LEVEL;
            case FULL -> 33;
            case BLOCK_TICKING -> 32;
            case ENTITY_TICKING -> 31;
        };
    }

    public static boolean isEntityTicking(int level) {
        return level <= 31;
    }

    public static boolean isBlockTicking(int level) {
        return level <= 32;
    }

    public static boolean isLoaded(int level) {
        return level <= MAX_LEVEL;
    }
}
