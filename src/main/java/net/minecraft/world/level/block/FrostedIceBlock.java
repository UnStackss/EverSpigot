package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class FrostedIceBlock extends IceBlock {
    public static final MapCodec<FrostedIceBlock> CODEC = simpleCodec(FrostedIceBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final int NEIGHBORS_TO_AGE = 4;
    private static final int NEIGHBORS_TO_MELT = 2;

    @Override
    public MapCodec<FrostedIceBlock> codec() {
        return CODEC;
    }

    public FrostedIceBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleTick(pos, this, Mth.nextInt(world.getRandom(), 60, 120));
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((random.nextInt(3) == 0 || this.fewerNeigboursThan(world, pos, 4))
            && world.getMaxLocalRawBrightness(pos) > 11 - state.getValue(AGE) - state.getLightBlock(world, pos)
            && this.slightlyMelt(state, world, pos)) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Direction direction : Direction.values()) {
                mutableBlockPos.setWithOffset(pos, direction);
                BlockState blockState = world.getBlockState(mutableBlockPos);
                if (blockState.is(this) && !this.slightlyMelt(blockState, world, mutableBlockPos)) {
                    world.scheduleTick(mutableBlockPos, this, Mth.nextInt(random, 20, 40));
                }
            }
        } else {
            world.scheduleTick(pos, this, Mth.nextInt(random, 20, 40));
        }
    }

    private boolean slightlyMelt(BlockState state, Level world, BlockPos pos) {
        int i = state.getValue(AGE);
        if (i < 3) {
            world.setBlock(pos, state.setValue(AGE, Integer.valueOf(i + 1)), 2);
            return false;
        } else {
            this.melt(state, world, pos);
            return true;
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (sourceBlock.defaultBlockState().is(this) && this.fewerNeigboursThan(world, pos, 2)) {
            this.melt(state, world, pos);
        }

        super.neighborChanged(state, world, pos, sourceBlock, sourcePos, notify);
    }

    private boolean fewerNeigboursThan(BlockGetter world, BlockPos pos, int maxNeighbors) {
        int i = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            mutableBlockPos.setWithOffset(pos, direction);
            if (world.getBlockState(mutableBlockPos).is(this)) {
                if (++i >= maxNeighbors) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
