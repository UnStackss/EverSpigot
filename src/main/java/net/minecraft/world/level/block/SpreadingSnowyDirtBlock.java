package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {

    protected SpreadingSnowyDirtBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    private static boolean canBeGrass(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.above();
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        if (iblockdata1.is(Blocks.SNOW) && (Integer) iblockdata1.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        } else if (iblockdata1.getFluidState().getAmount() == 8) {
            return false;
        } else {
            int i = LightEngine.getLightBlockInto(world, state, pos, iblockdata1, blockposition1, Direction.UP, iblockdata1.getLightBlock(world, blockposition1));

            return i < world.getMaxLightLevel();
        }
    }

    @Override
    protected abstract MapCodec<? extends SpreadingSnowyDirtBlock> codec();

    private static boolean canPropagate(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.above();

        return SpreadingSnowyDirtBlock.canBeGrass(state, world, pos) && !world.getFluidState(blockposition1).is(FluidTags.WATER);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!SpreadingSnowyDirtBlock.canBeGrass(state, world, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, Blocks.DIRT.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else {
            if (world.getMaxLocalRawBrightness(pos.above()) >= 9) {
                BlockState iblockdata1 = this.defaultBlockState();

                for (int i = 0; i < 4; ++i) {
                    BlockPos blockposition1 = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);

                    if (world.getBlockState(blockposition1).is(Blocks.DIRT) && SpreadingSnowyDirtBlock.canPropagate(iblockdata1, world, blockposition1)) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition1, (BlockState) iblockdata1.setValue(SpreadingSnowyDirtBlock.SNOWY, world.getBlockState(blockposition1.above()).is(Blocks.SNOW))); // CraftBukkit
                    }
                }
            }

        }
    }
}
