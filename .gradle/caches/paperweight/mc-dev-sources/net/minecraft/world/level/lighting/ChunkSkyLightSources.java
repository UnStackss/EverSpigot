package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChunkSkyLightSources {
    private static final int SIZE = 16;
    public static final int NEGATIVE_INFINITY = Integer.MIN_VALUE;
    private final int minY;
    private final BitStorage heightmap;
    private final BlockPos.MutableBlockPos mutablePos1 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos mutablePos2 = new BlockPos.MutableBlockPos();

    public ChunkSkyLightSources(LevelHeightAccessor heightLimitView) {
        this.minY = heightLimitView.getMinBuildHeight() - 1;
        int i = heightLimitView.getMaxBuildHeight();
        int j = Mth.ceillog2(i - this.minY + 1);
        this.heightmap = new SimpleBitStorage(j, 256);
    }

    public void fillFrom(ChunkAccess chunk) {
        int i = chunk.getHighestFilledSectionIndex();
        if (i == -1) {
            this.fill(this.minY);
        } else {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    int l = Math.max(this.findLowestSourceY(chunk, i, k, j), this.minY);
                    this.set(index(k, j), l);
                }
            }
        }
    }

    private int findLowestSourceY(ChunkAccess chunk, int topSectionIndex, int localX, int localZ) {
        int i = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(topSectionIndex) + 1);
        BlockPos.MutableBlockPos mutableBlockPos = this.mutablePos1.set(localX, i, localZ);
        BlockPos.MutableBlockPos mutableBlockPos2 = this.mutablePos2.setWithOffset(mutableBlockPos, Direction.DOWN);
        BlockState blockState = Blocks.AIR.defaultBlockState();

        for (int j = topSectionIndex; j >= 0; j--) {
            LevelChunkSection levelChunkSection = chunk.getSection(j);
            if (levelChunkSection.hasOnlyAir()) {
                blockState = Blocks.AIR.defaultBlockState();
                int k = chunk.getSectionYFromSectionIndex(j);
                mutableBlockPos.setY(SectionPos.sectionToBlockCoord(k));
                mutableBlockPos2.setY(mutableBlockPos.getY() - 1);
            } else {
                for (int l = 15; l >= 0; l--) {
                    BlockState blockState2 = levelChunkSection.getBlockState(localX, l, localZ);
                    if (isEdgeOccluded(chunk, mutableBlockPos, blockState, mutableBlockPos2, blockState2)) {
                        return mutableBlockPos.getY();
                    }

                    blockState = blockState2;
                    mutableBlockPos.set(mutableBlockPos2);
                    mutableBlockPos2.move(Direction.DOWN);
                }
            }
        }

        return this.minY;
    }

    public boolean update(BlockGetter blockView, int localX, int y, int localZ) {
        int i = y + 1;
        int j = index(localX, localZ);
        int k = this.get(j);
        if (i < k) {
            return false;
        } else {
            BlockPos blockPos = this.mutablePos1.set(localX, y + 1, localZ);
            BlockState blockState = blockView.getBlockState(blockPos);
            BlockPos blockPos2 = this.mutablePos2.set(localX, y, localZ);
            BlockState blockState2 = blockView.getBlockState(blockPos2);
            if (this.updateEdge(blockView, j, k, blockPos, blockState, blockPos2, blockState2)) {
                return true;
            } else {
                BlockPos blockPos3 = this.mutablePos1.set(localX, y - 1, localZ);
                BlockState blockState3 = blockView.getBlockState(blockPos3);
                return this.updateEdge(blockView, j, k, blockPos2, blockState2, blockPos3, blockState3);
            }
        }
    }

    private boolean updateEdge(
        BlockGetter blockView, int packedIndex, int value, BlockPos upperPos, BlockState upperState, BlockPos lowerPos, BlockState lowerState
    ) {
        int i = upperPos.getY();
        if (isEdgeOccluded(blockView, upperPos, upperState, lowerPos, lowerState)) {
            if (i > value) {
                this.set(packedIndex, i);
                return true;
            }
        } else if (i == value) {
            this.set(packedIndex, this.findLowestSourceBelow(blockView, lowerPos, lowerState));
            return true;
        }

        return false;
    }

    private int findLowestSourceBelow(BlockGetter blockView, BlockPos pos, BlockState blockState) {
        BlockPos.MutableBlockPos mutableBlockPos = this.mutablePos1.set(pos);
        BlockPos.MutableBlockPos mutableBlockPos2 = this.mutablePos2.setWithOffset(pos, Direction.DOWN);
        BlockState blockState2 = blockState;

        while (mutableBlockPos2.getY() >= this.minY) {
            BlockState blockState3 = blockView.getBlockState(mutableBlockPos2);
            if (isEdgeOccluded(blockView, mutableBlockPos, blockState2, mutableBlockPos2, blockState3)) {
                return mutableBlockPos.getY();
            }

            blockState2 = blockState3;
            mutableBlockPos.set(mutableBlockPos2);
            mutableBlockPos2.move(Direction.DOWN);
        }

        return this.minY;
    }

    private static boolean isEdgeOccluded(BlockGetter blockView, BlockPos upperPos, BlockState upperState, BlockPos lowerPos, BlockState lowerState) {
        if (lowerState.getLightBlock(blockView, lowerPos) != 0) {
            return true;
        } else {
            VoxelShape voxelShape = LightEngine.getOcclusionShape(blockView, upperPos, upperState, Direction.DOWN);
            VoxelShape voxelShape2 = LightEngine.getOcclusionShape(blockView, lowerPos, lowerState, Direction.UP);
            return Shapes.faceShapeOccludes(voxelShape, voxelShape2);
        }
    }

    public int getLowestSourceY(int localX, int localZ) {
        int i = this.get(index(localX, localZ));
        return this.extendSourcesBelowWorld(i);
    }

    public int getHighestLowestSourceY() {
        int i = Integer.MIN_VALUE;

        for (int j = 0; j < this.heightmap.getSize(); j++) {
            int k = this.heightmap.get(j);
            if (k > i) {
                i = k;
            }
        }

        return this.extendSourcesBelowWorld(i + this.minY);
    }

    private void fill(int y) {
        int i = y - this.minY;

        for (int j = 0; j < this.heightmap.getSize(); j++) {
            this.heightmap.set(j, i);
        }
    }

    private void set(int index, int y) {
        this.heightmap.set(index, y - this.minY);
    }

    private int get(int index) {
        return this.heightmap.get(index) + this.minY;
    }

    private int extendSourcesBelowWorld(int y) {
        return y == this.minY ? Integer.MIN_VALUE : y;
    }

    private static int index(int localX, int localZ) {
        return localX + localZ * 16;
    }
}
