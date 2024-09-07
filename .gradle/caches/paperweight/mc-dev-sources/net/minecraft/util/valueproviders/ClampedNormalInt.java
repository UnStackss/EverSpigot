package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedNormalInt extends IntProvider {
    public static final MapCodec<ClampedNormalInt> CODEC = RecordCodecBuilder.<ClampedNormalInt>mapCodec(
            instance -> instance.group(
                        Codec.FLOAT.fieldOf("mean").forGetter(provider -> provider.mean),
                        Codec.FLOAT.fieldOf("deviation").forGetter(provider -> provider.deviation),
                        Codec.INT.fieldOf("min_inclusive").forGetter(provider -> provider.minInclusive),
                        Codec.INT.fieldOf("max_inclusive").forGetter(provider -> provider.maxInclusive)
                    )
                    .apply(instance, ClampedNormalInt::new)
        )
        .validate(
            provider -> provider.maxInclusive < provider.minInclusive
                    ? DataResult.error(() -> "Max must be larger than min: [" + provider.minInclusive + ", " + provider.maxInclusive + "]")
                    : DataResult.success(provider)
        );
    private final float mean;
    private final float deviation;
    private final int minInclusive;
    private final int maxInclusive;

    public static ClampedNormalInt of(float mean, float deviation, int min, int max) {
        return new ClampedNormalInt(mean, deviation, min, max);
    }

    private ClampedNormalInt(float mean, float deviation, int min, int max) {
        this.mean = mean;
        this.deviation = deviation;
        this.minInclusive = min;
        this.maxInclusive = max;
    }

    @Override
    public int sample(RandomSource random) {
        return sample(random, this.mean, this.deviation, (float)this.minInclusive, (float)this.maxInclusive);
    }

    public static int sample(RandomSource random, float mean, float deviation, float min, float max) {
        return (int)Mth.clamp(Mth.normal(random, mean, deviation), min, max);
    }

    @Override
    public int getMinValue() {
        return this.minInclusive;
    }

    @Override
    public int getMaxValue() {
        return this.maxInclusive;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.CLAMPED_NORMAL;
    }

    @Override
    public String toString() {
        return "normal(" + this.mean + ", " + this.deviation + ") in [" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
