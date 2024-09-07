package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MangroveLeavesBlock extends LeavesBlock implements BonemealableBlock {
    public static final MapCodec<MangroveLeavesBlock> CODEC = simpleCodec(MangroveLeavesBlock::new);

    @Override
    public MapCodec<MangroveLeavesBlock> codec() {
        return CODEC;
    }

    public MangroveLeavesBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return world.getBlockState(pos.below()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        world.setBlock(pos.below(), MangrovePropaguleBlock.createNewHangingPropagule(), 2);
    }

    @Override
    public BlockPos getParticlePos(BlockPos pos) {
        return pos.below();
    }
}
