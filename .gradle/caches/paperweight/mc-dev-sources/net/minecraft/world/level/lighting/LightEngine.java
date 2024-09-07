package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class LightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>> implements LayerLightEventListener {
    public static final int MAX_LEVEL = 15;
    protected static final int MIN_OPACITY = 1;
    protected static final long PULL_LIGHT_IN_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(1);
    private static final int MIN_QUEUE_SIZE = 512;
    protected static final Direction[] PROPAGATION_DIRECTIONS = Direction.values();
    protected final LightChunkGetter chunkSource;
    protected final S storage;
    private final LongOpenHashSet blockNodesToCheck = new LongOpenHashSet(512, 0.5F);
    private final LongArrayFIFOQueue decreaseQueue = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue increaseQueue = new LongArrayFIFOQueue();
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private static final int CACHE_SIZE = 2;
    private final long[] lastChunkPos = new long[2];
    private final LightChunk[] lastChunk = new LightChunk[2];

    protected LightEngine(LightChunkGetter chunkProvider, S lightStorage) {
        this.chunkSource = chunkProvider;
        this.storage = lightStorage;
        this.clearChunkCache();
    }

    public static boolean hasDifferentLightProperties(BlockGetter blockView, BlockPos pos, BlockState oldState, BlockState newState) {
        return newState != oldState
            && (
                newState.getLightBlock(blockView, pos) != oldState.getLightBlock(blockView, pos)
                    || newState.getLightEmission() != oldState.getLightEmission()
                    || newState.useShapeForLightOcclusion()
                    || oldState.useShapeForLightOcclusion()
            );
    }

    public static int getLightBlockInto(
        BlockGetter world, BlockState state1, BlockPos pos1, BlockState state2, BlockPos pos2, Direction direction, int opacity2
    ) {
        boolean bl = isEmptyShape(state1);
        boolean bl2 = isEmptyShape(state2);
        if (bl && bl2) {
            return opacity2;
        } else {
            VoxelShape voxelShape = bl ? Shapes.empty() : state1.getOcclusionShape(world, pos1);
            VoxelShape voxelShape2 = bl2 ? Shapes.empty() : state2.getOcclusionShape(world, pos2);
            return Shapes.mergedFaceOccludes(voxelShape, voxelShape2, direction) ? 16 : opacity2;
        }
    }

    public static VoxelShape getOcclusionShape(BlockGetter blockView, BlockPos pos, BlockState blockState, Direction direction) {
        return isEmptyShape(blockState) ? Shapes.empty() : blockState.getFaceOcclusionShape(blockView, pos, direction);
    }

    protected static boolean isEmptyShape(BlockState blockState) {
        return !blockState.canOcclude() || !blockState.useShapeForLightOcclusion();
    }

    protected BlockState getState(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        LightChunk lightChunk = this.getChunk(i, j);
        return lightChunk == null ? Blocks.BEDROCK.defaultBlockState() : lightChunk.getBlockState(pos);
    }

    protected int getOpacity(BlockState state, BlockPos pos) {
        return Math.max(1, state.getLightBlock(this.chunkSource.getLevel(), pos));
    }

    protected boolean shapeOccludes(long sourceId, BlockState sourceState, long targetId, BlockState targetState, Direction direction) {
        VoxelShape voxelShape = this.getOcclusionShape(sourceState, sourceId, direction);
        VoxelShape voxelShape2 = this.getOcclusionShape(targetState, targetId, direction.getOpposite());
        return Shapes.faceShapeOccludes(voxelShape, voxelShape2);
    }

    protected VoxelShape getOcclusionShape(BlockState blockState, long pos, Direction direction) {
        return getOcclusionShape(this.chunkSource.getLevel(), this.mutablePos.set(pos), blockState, direction);
    }

    @Nullable
    protected LightChunk getChunk(int chunkX, int chunkZ) {
        long l = ChunkPos.asLong(chunkX, chunkZ);

        for (int i = 0; i < 2; i++) {
            if (l == this.lastChunkPos[i]) {
                return this.lastChunk[i];
            }
        }

        LightChunk lightChunk = this.chunkSource.getChunkForLighting(chunkX, chunkZ);

        for (int j = 1; j > 0; j--) {
            this.lastChunkPos[j] = this.lastChunkPos[j - 1];
            this.lastChunk[j] = this.lastChunk[j - 1];
        }

        this.lastChunkPos[0] = l;
        this.lastChunk[0] = lightChunk;
        return lightChunk;
    }

    private void clearChunkCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunk, null);
    }

    @Override
    public void checkBlock(BlockPos pos) {
        this.blockNodesToCheck.add(pos.asLong());
    }

    public void queueSectionData(long sectionPos, @Nullable DataLayer lightArray) {
        this.storage.queueSectionData(sectionPos, lightArray);
    }

    public void retainData(ChunkPos pos, boolean retainData) {
        this.storage.retainData(SectionPos.getZeroNode(pos.x, pos.z), retainData);
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean notReady) {
        this.storage.updateSectionStatus(pos.asLong(), notReady);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean retainData) {
        this.storage.setLightEnabled(SectionPos.getZeroNode(pos.x, pos.z), retainData);
    }

    @Override
    public int runLightUpdates() {
        LongIterator longIterator = this.blockNodesToCheck.iterator();

        while (longIterator.hasNext()) {
            this.checkNode(longIterator.nextLong());
        }

        this.blockNodesToCheck.clear();
        this.blockNodesToCheck.trim(512);
        int i = 0;
        i += this.propagateDecreases();
        i += this.propagateIncreases();
        this.clearChunkCache();
        this.storage.markNewInconsistencies(this);
        this.storage.swapSectionMap();
        return i;
    }

    private int propagateIncreases() {
        int i;
        for (i = 0; !this.increaseQueue.isEmpty(); i++) {
            long l = this.increaseQueue.dequeueLong();
            long m = this.increaseQueue.dequeueLong();
            int j = this.storage.getStoredLevel(l);
            int k = LightEngine.QueueEntry.getFromLevel(m);
            if (LightEngine.QueueEntry.isIncreaseFromEmission(m) && j < k) {
                this.storage.setStoredLevel(l, k);
                j = k;
            }

            if (j == k) {
                this.propagateIncrease(l, m, j);
            }
        }

        return i;
    }

    private int propagateDecreases() {
        int i;
        for (i = 0; !this.decreaseQueue.isEmpty(); i++) {
            long l = this.decreaseQueue.dequeueLong();
            long m = this.decreaseQueue.dequeueLong();
            this.propagateDecrease(l, m);
        }

        return i;
    }

    protected void enqueueDecrease(long blockPos, long flags) {
        this.decreaseQueue.enqueue(blockPos);
        this.decreaseQueue.enqueue(flags);
    }

    protected void enqueueIncrease(long blockPos, long flags) {
        this.increaseQueue.enqueue(blockPos);
        this.increaseQueue.enqueue(flags);
    }

    @Override
    public boolean hasLightWork() {
        return this.storage.hasInconsistencies() || !this.blockNodesToCheck.isEmpty() || !this.decreaseQueue.isEmpty() || !this.increaseQueue.isEmpty();
    }

    @Nullable
    @Override
    public DataLayer getDataLayerData(SectionPos pos) {
        return this.storage.getDataLayerData(pos.asLong());
    }

    @Override
    public int getLightValue(BlockPos pos) {
        return this.storage.getLightValue(pos.asLong());
    }

    public String getDebugData(long sectionPos) {
        return this.getDebugSectionType(sectionPos).display();
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return this.storage.getDebugSectionType(sectionPos);
    }

    protected abstract void checkNode(long blockPos);

    protected abstract void propagateIncrease(long blockPos, long l, int lightLevel);

    protected abstract void propagateDecrease(long blockPos, long l);

    public static class QueueEntry {
        private static final int FROM_LEVEL_BITS = 4;
        private static final int DIRECTION_BITS = 6;
        private static final long LEVEL_MASK = 15L;
        private static final long DIRECTIONS_MASK = 1008L;
        private static final long FLAG_FROM_EMPTY_SHAPE = 1024L;
        private static final long FLAG_INCREASE_FROM_EMISSION = 2048L;

        public static long decreaseSkipOneDirection(int lightLevel, Direction direction) {
            long l = withoutDirection(1008L, direction);
            return withLevel(l, lightLevel);
        }

        public static long decreaseAllDirections(int lightLevel) {
            return withLevel(1008L, lightLevel);
        }

        public static long increaseLightFromEmission(int lightLevel, boolean trivial) {
            long l = 1008L;
            l |= 2048L;
            if (trivial) {
                l |= 1024L;
            }

            return withLevel(l, lightLevel);
        }

        public static long increaseSkipOneDirection(int lightLevel, boolean trivial, Direction direction) {
            long l = withoutDirection(1008L, direction);
            if (trivial) {
                l |= 1024L;
            }

            return withLevel(l, lightLevel);
        }

        public static long increaseOnlyOneDirection(int lightLevel, boolean trivial, Direction direction) {
            long l = 0L;
            if (trivial) {
                l |= 1024L;
            }

            l = withDirection(l, direction);
            return withLevel(l, lightLevel);
        }

        public static long increaseSkySourceInDirections(boolean down, boolean north, boolean south, boolean west, boolean east) {
            long l = withLevel(0L, 15);
            if (down) {
                l = withDirection(l, Direction.DOWN);
            }

            if (north) {
                l = withDirection(l, Direction.NORTH);
            }

            if (south) {
                l = withDirection(l, Direction.SOUTH);
            }

            if (west) {
                l = withDirection(l, Direction.WEST);
            }

            if (east) {
                l = withDirection(l, Direction.EAST);
            }

            return l;
        }

        public static int getFromLevel(long packed) {
            return (int)(packed & 15L);
        }

        public static boolean isFromEmptyShape(long packed) {
            return (packed & 1024L) != 0L;
        }

        public static boolean isIncreaseFromEmission(long packed) {
            return (packed & 2048L) != 0L;
        }

        public static boolean shouldPropagateInDirection(long packed, Direction direction) {
            return (packed & 1L << direction.ordinal() + 4) != 0L;
        }

        private static long withLevel(long packed, int lightLevel) {
            return packed & -16L | (long)lightLevel & 15L;
        }

        private static long withDirection(long packed, Direction direction) {
            return packed | 1L << direction.ordinal() + 4;
        }

        private static long withoutDirection(long packed, Direction direction) {
            return packed & ~(1L << direction.ordinal() + 4);
        }
    }
}
