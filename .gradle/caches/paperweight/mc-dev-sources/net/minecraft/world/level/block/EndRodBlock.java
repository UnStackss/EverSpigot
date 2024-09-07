package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class EndRodBlock extends RodBlock {
    public static final MapCodec<EndRodBlock> CODEC = simpleCodec(EndRodBlock::new);

    @Override
    public MapCodec<EndRodBlock> codec() {
        return CODEC;
    }

    protected EndRodBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction direction = ctx.getClickedFace();
        BlockState blockState = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(direction.getOpposite()));
        return blockState.is(this) && blockState.getValue(FACING) == direction
            ? this.defaultBlockState().setValue(FACING, direction.getOpposite())
            : this.defaultBlockState().setValue(FACING, direction);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        double d = (double)pos.getX() + 0.55 - (double)(random.nextFloat() * 0.1F);
        double e = (double)pos.getY() + 0.55 - (double)(random.nextFloat() * 0.1F);
        double f = (double)pos.getZ() + 0.55 - (double)(random.nextFloat() * 0.1F);
        double g = (double)(0.4F - (random.nextFloat() + random.nextFloat()) * 0.4F);
        if (random.nextInt(5) == 0) {
            world.addParticle(
                ParticleTypes.END_ROD,
                d + (double)direction.getStepX() * g,
                e + (double)direction.getStepY() * g,
                f + (double)direction.getStepZ() * g,
                random.nextGaussian() * 0.005,
                random.nextGaussian() * 0.005,
                random.nextGaussian() * 0.005
            );
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
