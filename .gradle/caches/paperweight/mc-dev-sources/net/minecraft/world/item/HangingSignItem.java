package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

public class HangingSignItem extends SignItem {
    public HangingSignItem(Block hangingSign, Block wallHangingSign, Item.Properties settings) {
        super(settings, hangingSign, wallHangingSign, Direction.UP);
    }

    @Override
    protected boolean canPlace(LevelReader world, BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof WallHangingSignBlock wallHangingSignBlock && !wallHangingSignBlock.canPlace(state, world, pos)) {
            return false;
        }

        return super.canPlace(world, state, pos);
    }
}
