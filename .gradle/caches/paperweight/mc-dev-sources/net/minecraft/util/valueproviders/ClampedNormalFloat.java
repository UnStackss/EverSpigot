package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedNormalFloat extends FloatProvider {
    public static final MapCodec<ClampedNormalFloat> CODEC = RecordCodecBuilder.<ClampedNormalFloat>mapCodec(
            instance -> instance.group(
                        Codec.FLOAT.fieldOf("mean").forGetter(provider -> provider.mean),
                        Codec.FLOAT.fieldOf("deviation").forGetter(provider -> provider.deviation),
                        Codec.FLOAT.fieldOf("min").forGetter(provider -> provider.min),
                        Codec.FLOAT.fieldOf("max").forGetter(provider -> provider.max)
                    )
                    .apply(instance, ClampedNormalFloat::new)
        )
        .validate(
            provider -> provider.max < provider.min
                    ? DataResult.error(() -> "Max must be larger than min: [" + provider.min + ", " + provider.max + "]")
                    : DataResult.success(provider)
        );
    private final float mean;
    private final float deviation;
    private final float min;
    private final float max;

    public static ClampedNormalFloat of(float mean, float deviation, float min, float max) {
        return new ClampedNormalFloat(mean, deviation, min, max);
    }

    private ClampedNormalFloat(float mean, float deviation, float min, float max) {
        this.mean = mean;
        this.deviation = deviation;
        this.min = min;
        this.max = max;
    }

    @Override
    public float sample(RandomSource random) {
        return sample(random, this.mean, this.deviation, this.min, this.max);
    }

    public static float sample(RandomSource random, float mean, float deviation, float min, float max) {
        return Mth.clamp(Mth.normal(random, mean, deviation), min, max);
    }

    @Override
    public float getMinValue() {
        return this.min;
    }

    @Override
    public float getMaxValue() {
        return this.max;
    }

    @Override
    public FloatProviderType<?> getType() {
        return FloatProviderType.CLAMPED_NORMAL;
    }

    @Override
    public String toString() {
        return "normal(" + this.mean + ", " + this.deviation + ") in [" + this.min + "-" + this.max + "]";
    }
}
