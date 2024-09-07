package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

public class AmbientParticleSettings {
    public static final Codec<AmbientParticleSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ParticleTypes.CODEC.fieldOf("options").forGetter(config -> config.options),
                    Codec.FLOAT.fieldOf("probability").forGetter(config -> config.probability)
                )
                .apply(instance, AmbientParticleSettings::new)
    );
    private final ParticleOptions options;
    private final float probability;

    public AmbientParticleSettings(ParticleOptions particle, float probability) {
        this.options = particle;
        this.probability = probability;
    }

    public ParticleOptions getOptions() {
        return this.options;
    }

    public boolean canSpawn(RandomSource random) {
        return random.nextFloat() <= this.probability;
    }
}
