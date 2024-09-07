package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MyceliumBlock extends SpreadingSnowyDirtBlock {
    public static final MapCodec<MyceliumBlock> CODEC = simpleCodec(MyceliumBlock::new);

    @Override
    public MapCodec<MyceliumBlock> codec() {
        return CODEC;
    }

    public MyceliumBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        super.animateTick(state, world, pos, random);
        if (random.nextInt(10) == 0) {
            world.addParticle(
                ParticleTypes.MYCELIUM,
                (double)pos.getX() + random.nextDouble(),
                (double)pos.getY() + 1.1,
                (double)pos.getZ() + random.nextDouble(),
                0.0,
                0.0,
                0.0
            );
        }
    }
}
