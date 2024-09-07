package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class NoiseBasedCountPlacement extends RepeatingPlacement {
    public static final MapCodec<NoiseBasedCountPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.INT.fieldOf("noise_to_count_ratio").forGetter(noiseBasedCountPlacement -> noiseBasedCountPlacement.noiseToCountRatio),
                    Codec.DOUBLE.fieldOf("noise_factor").forGetter(noiseBasedCountPlacement -> noiseBasedCountPlacement.noiseFactor),
                    Codec.DOUBLE.fieldOf("noise_offset").orElse(0.0).forGetter(noiseBasedCountPlacement -> noiseBasedCountPlacement.noiseOffset)
                )
                .apply(instance, NoiseBasedCountPlacement::new)
    );
    private final int noiseToCountRatio;
    private final double noiseFactor;
    private final double noiseOffset;

    private NoiseBasedCountPlacement(int noiseToCountRatio, double noiseFactor, double noiseOffset) {
        this.noiseToCountRatio = noiseToCountRatio;
        this.noiseFactor = noiseFactor;
        this.noiseOffset = noiseOffset;
    }

    public static NoiseBasedCountPlacement of(int noiseToCountRatio, double noiseFactor, double noiseOffset) {
        return new NoiseBasedCountPlacement(noiseToCountRatio, noiseFactor, noiseOffset);
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        double d = Biome.BIOME_INFO_NOISE.getValue((double)pos.getX() / this.noiseFactor, (double)pos.getZ() / this.noiseFactor, false);
        return (int)Math.ceil((d + this.noiseOffset) * (double)this.noiseToCountRatio);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_BASED_COUNT;
    }
}
