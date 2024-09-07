package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SoulFireBlock extends BaseFireBlock {
    public static final MapCodec<SoulFireBlock> CODEC = simpleCodec(SoulFireBlock::new);

    @Override
    public MapCodec<SoulFireBlock> codec() {
        return CODEC;
    }

    public SoulFireBlock(BlockBehaviour.Properties settings) {
        super(settings, 2.0F);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return this.canSurvive(state, world, pos) ? this.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return canSurviveOnBlock(world.getBlockState(pos.below()));
    }

    public static boolean canSurviveOnBlock(BlockState state) {
        return state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }
}
