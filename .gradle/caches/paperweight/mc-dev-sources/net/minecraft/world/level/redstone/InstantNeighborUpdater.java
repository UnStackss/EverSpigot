package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class InstantNeighborUpdater implements NeighborUpdater {
    private final Level level;

    public InstantNeighborUpdater(Level world) {
        this.level = world;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        NeighborUpdater.executeShapeUpdate(this.level, direction, neighborState, pos, neighborPos, flags, maxUpdateDepth - 1);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {
        BlockState blockState = this.level.getBlockState(pos);
        this.neighborChanged(blockState, pos, sourceBlock, sourcePos, false);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        NeighborUpdater.executeUpdate(this.level, state, pos, sourceBlock, sourcePos, notify);
    }
}
