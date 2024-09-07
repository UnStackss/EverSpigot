package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantBodyBlock extends GrowingPlantBlock implements BonemealableBlock {
    protected GrowingPlantBodyBlock(BlockBehaviour.Properties settings, Direction growthDirection, VoxelShape outlineShape, boolean tickWater) {
        super(settings, growthDirection, outlineShape, tickWater);
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantBodyBlock> codec();

    protected BlockState updateHeadAfterConvertedFromBody(BlockState from, BlockState to) {
        return to;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == this.growthDirection.getOpposite() && !state.canSurvive(world, pos)) {
            world.scheduleTick(pos, this, 1);
        }

        GrowingPlantHeadBlock growingPlantHeadBlock = this.getHeadBlock();
        if (direction == this.growthDirection && !neighborState.is(this) && !neighborState.is(growingPlantHeadBlock)) {
            return this.updateHeadAfterConvertedFromBody(state, growingPlantHeadBlock.getStateForPlacement(world));
        } else {
            if (this.scheduleFluidTicks) {
                world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
            }

            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return new ItemStack(this.getHeadBlock());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        Optional<BlockPos> optional = this.getHeadPos(world, pos, state.getBlock());
        return optional.isPresent() && this.getHeadBlock().canGrowInto(world.getBlockState(optional.get().relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        Optional<BlockPos> optional = this.getHeadPos(world, pos, state.getBlock());
        if (optional.isPresent()) {
            BlockState blockState = world.getBlockState(optional.get());
            ((GrowingPlantHeadBlock)blockState.getBlock()).performBonemeal(world, random, optional.get(), blockState);
        }
    }

    private Optional<BlockPos> getHeadPos(BlockGetter world, BlockPos pos, Block block) {
        return BlockUtil.getTopConnectedBlock(world, pos, block, this.growthDirection, this.getHeadBlock());
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        boolean bl = super.canBeReplaced(state, context);
        return (!bl || !context.getItemInHand().is(this.getHeadBlock().asItem())) && bl;
    }

    @Override
    protected Block getBodyBlock() {
        return this;
    }
}
