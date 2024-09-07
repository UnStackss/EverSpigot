package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;

public class KelpPlantBlock extends GrowingPlantBodyBlock implements LiquidBlockContainer {
    public static final MapCodec<KelpPlantBlock> CODEC = simpleCodec(KelpPlantBlock::new);

    @Override
    public MapCodec<KelpPlantBlock> codec() {
        return CODEC;
    }

    protected KelpPlantBlock(BlockBehaviour.Properties settings) {
        super(settings, Direction.UP, Shapes.block(), true);
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return (GrowingPlantHeadBlock)Blocks.KELP;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    protected boolean canAttachTo(BlockState state) {
        return this.getHeadBlock().canAttachTo(state);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter world, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor world, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
