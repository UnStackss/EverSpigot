package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class RepeaterBlock extends DiodeBlock {
    public static final MapCodec<RepeaterBlock> CODEC = simpleCodec(RepeaterBlock::new);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final IntegerProperty DELAY = BlockStateProperties.DELAY;

    @Override
    public MapCodec<RepeaterBlock> codec() {
        return CODEC;
    }

    protected RepeaterBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(DELAY, Integer.valueOf(1))
                .setValue(LOCKED, Boolean.valueOf(false))
                .setValue(POWERED, Boolean.valueOf(false))
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            world.setBlock(pos, state.cycle(DELAY), 3);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    @Override
    protected int getDelay(BlockState state) {
        return state.getValue(DELAY) * 2;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = super.getStateForPlacement(ctx);
        return blockState.setValue(LOCKED, Boolean.valueOf(this.isLocked(ctx.getLevel(), ctx.getClickedPos(), blockState)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !this.canSurviveOn(world, neighborPos, neighborState)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            return !world.isClientSide() && direction.getAxis() != state.getValue(FACING).getAxis()
                ? state.setValue(LOCKED, Boolean.valueOf(this.isLocked(world, pos, state)))
                : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    public boolean isLocked(LevelReader world, BlockPos pos, BlockState state) {
        return this.getAlternateSignal(world, pos, state) > 0;
    }

    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            Direction direction = state.getValue(FACING);
            double d = (double)pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double e = (double)pos.getY() + 0.4 + (random.nextDouble() - 0.5) * 0.2;
            double f = (double)pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            float g = -5.0F;
            if (random.nextBoolean()) {
                g = (float)(state.getValue(DELAY) * 2 - 1);
            }

            g /= 16.0F;
            double h = (double)(g * (float)direction.getStepX());
            double i = (double)(g * (float)direction.getStepZ());
            world.addParticle(DustParticleOptions.REDSTONE, d + h, e, f + i, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DELAY, LOCKED, POWERED);
    }
}
