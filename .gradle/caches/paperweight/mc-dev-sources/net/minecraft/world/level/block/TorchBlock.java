package net.minecraft.world.level.block;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class TorchBlock extends BaseTorchBlock {
    protected static final MapCodec<SimpleParticleType> PARTICLE_OPTIONS_FIELD = BuiltInRegistries.PARTICLE_TYPE
        .byNameCodec()
        .comapFlatMap(
            particleType -> particleType instanceof SimpleParticleType simpleParticleType
                    ? DataResult.success(simpleParticleType)
                    : DataResult.error(() -> "Not a SimpleParticleType: " + particleType),
            particleType -> (ParticleType<?>)particleType
        )
        .fieldOf("particle_options");
    public static final MapCodec<TorchBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(PARTICLE_OPTIONS_FIELD.forGetter(block -> block.flameParticle), propertiesCodec()).apply(instance, TorchBlock::new)
    );
    protected final SimpleParticleType flameParticle;

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return CODEC;
    }

    protected TorchBlock(SimpleParticleType particle, BlockBehaviour.Properties settings) {
        super(settings);
        this.flameParticle = particle;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        double d = (double)pos.getX() + 0.5;
        double e = (double)pos.getY() + 0.7;
        double f = (double)pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.SMOKE, d, e, f, 0.0, 0.0, 0.0);
        world.addParticle(this.flameParticle, d, e, f, 0.0, 0.0, 0.0);
    }
}
