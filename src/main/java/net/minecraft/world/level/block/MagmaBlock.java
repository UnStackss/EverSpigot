package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MagmaBlock extends Block {

    public static final MapCodec<MagmaBlock> CODEC = simpleCodec(MagmaBlock::new);
    private static final int BUBBLE_COLUMN_CHECK_DELAY = 20;

    @Override
    public MapCodec<MagmaBlock> codec() {
        return MagmaBlock.CODEC;
    }

    public MagmaBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity) {
            entity.hurt(world.damageSources().hotFloor().directBlock(world, pos), 1.0F); // CraftBukkit
        }

        super.stepOn(world, pos, state, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BubbleColumnBlock.updateColumn(world, pos.above(), state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && neighborState.is(Blocks.WATER)) {
            world.scheduleTick(pos, (Block) this, 20);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleTick(pos, (Block) this, 20);
    }
}
