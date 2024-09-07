package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class TintedGlassBlock extends TransparentBlock {
    public static final MapCodec<TintedGlassBlock> CODEC = simpleCodec(TintedGlassBlock::new);

    @Override
    public MapCodec<TintedGlassBlock> codec() {
        return CODEC;
    }

    public TintedGlassBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return world.getMaxLightLevel();
    }
}
