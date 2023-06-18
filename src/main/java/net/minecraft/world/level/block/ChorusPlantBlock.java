package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ChorusPlantBlock extends PipeBlock {
    public static final MapCodec<ChorusPlantBlock> CODEC = simpleCodec(ChorusPlantBlock::new);

    @Override
    public MapCodec<ChorusPlantBlock> codec() {
        return CODEC;
    }

    protected ChorusPlantBlock(BlockBehaviour.Properties settings) {
        super(0.3125F, settings);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(NORTH, Boolean.valueOf(false))
                .setValue(EAST, Boolean.valueOf(false))
                .setValue(SOUTH, Boolean.valueOf(false))
                .setValue(WEST, Boolean.valueOf(false))
                .setValue(UP, Boolean.valueOf(false))
                .setValue(DOWN, Boolean.valueOf(false))
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return this.defaultBlockState(); // Paper - add option to disable block updates
        return getStateWithConnections(ctx.getLevel(), ctx.getClickedPos(), this.defaultBlockState());
    }

    public static BlockState getStateWithConnections(BlockGetter world, BlockPos pos, BlockState state) {
        BlockState blockState = world.getBlockState(pos.below());
        BlockState blockState2 = world.getBlockState(pos.above());
        BlockState blockState3 = world.getBlockState(pos.north());
        BlockState blockState4 = world.getBlockState(pos.east());
        BlockState blockState5 = world.getBlockState(pos.south());
        BlockState blockState6 = world.getBlockState(pos.west());
        Block block = state.getBlock();
        return state.trySetValue(DOWN, Boolean.valueOf(blockState.is(block) || blockState.is(Blocks.CHORUS_FLOWER) || blockState.is(Blocks.END_STONE)))
            .trySetValue(UP, Boolean.valueOf(blockState2.is(block) || blockState2.is(Blocks.CHORUS_FLOWER)))
            .trySetValue(NORTH, Boolean.valueOf(blockState3.is(block) || blockState3.is(Blocks.CHORUS_FLOWER)))
            .trySetValue(EAST, Boolean.valueOf(blockState4.is(block) || blockState4.is(Blocks.CHORUS_FLOWER)))
            .trySetValue(SOUTH, Boolean.valueOf(blockState5.is(block) || blockState5.is(Blocks.CHORUS_FLOWER)))
            .trySetValue(WEST, Boolean.valueOf(blockState6.is(block) || blockState6.is(Blocks.CHORUS_FLOWER)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return state; // Paper - add option to disable block updates
        if (!state.canSurvive(world, pos)) {
            world.scheduleTick(pos, this, 1);
            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        } else {
            boolean bl = neighborState.is(this) || neighborState.is(Blocks.CHORUS_FLOWER) || direction == Direction.DOWN && neighborState.is(Blocks.END_STONE);
            return state.setValue(PROPERTY_BY_DIRECTION.get(direction), Boolean.valueOf(bl));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return; // Paper - add option to disable block updates
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return true; // Paper - add option to disable block updates
        BlockState blockState = world.getBlockState(pos.below());
        boolean bl = !world.getBlockState(pos.above()).isAir() && !blockState.isAir();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState2 = world.getBlockState(blockPos);
            if (blockState2.is(this)) {
                if (bl) {
                    return false;
                }

                BlockState blockState3 = world.getBlockState(blockPos.below());
                if (blockState3.is(this) || blockState3.is(Blocks.END_STONE)) {
                    return true;
                }
            }
        }

        return blockState.is(this) || blockState.is(Blocks.END_STONE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
